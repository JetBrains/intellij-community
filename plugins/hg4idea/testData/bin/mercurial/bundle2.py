# bundle2.py - generic container format to transmit arbitrary data.
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""Handling of the new bundle2 format

The goal of bundle2 is to act as an atomically packet to transmit a set of
payloads in an application agnostic way. It consist in a sequence of "parts"
that will be handed to and processed by the application layer.


General format architecture
===========================

The format is architectured as follow

 - magic string
 - stream level parameters
 - payload parts (any number)
 - end of stream marker.

the Binary format
============================

All numbers are unsigned and big-endian.

stream level parameters
------------------------

Binary format is as follow

:params size: int32

  The total number of Bytes used by the parameters

:params value: arbitrary number of Bytes

  A blob of `params size` containing the serialized version of all stream level
  parameters.

  The blob contains a space separated list of parameters. Parameters with value
  are stored in the form `<name>=<value>`. Both name and value are urlquoted.

  Empty name are obviously forbidden.

  Name MUST start with a letter. If this first letter is lower case, the
  parameter is advisory and can be safely ignored. However when the first
  letter is capital, the parameter is mandatory and the bundling process MUST
  stop if he is not able to proceed it.

  Stream parameters use a simple textual format for two main reasons:

  - Stream level parameters should remain simple and we want to discourage any
    crazy usage.
  - Textual data allow easy human inspection of a bundle2 header in case of
    troubles.

  Any Applicative level options MUST go into a bundle2 part instead.

Payload part
------------------------

Binary format is as follow

:header size: int32

  The total number of Bytes used by the part header. When the header is empty
  (size = 0) this is interpreted as the end of stream marker.

:header:

    The header defines how to interpret the part. It contains two piece of
    data: the part type, and the part parameters.

    The part type is used to route an application level handler, that can
    interpret payload.

    Part parameters are passed to the application level handler.  They are
    meant to convey information that will help the application level object to
    interpret the part payload.

    The binary format of the header is has follow

    :typesize: (one byte)

    :parttype: alphanumerical part name (restricted to [a-zA-Z0-9_:-]*)

    :partid: A 32bits integer (unique in the bundle) that can be used to refer
             to this part.

    :parameters:

        Part's parameter may have arbitrary content, the binary structure is::

            <mandatory-count><advisory-count><param-sizes><param-data>

        :mandatory-count: 1 byte, number of mandatory parameters

        :advisory-count:  1 byte, number of advisory parameters

        :param-sizes:

            N couple of bytes, where N is the total number of parameters. Each
            couple contains (<size-of-key>, <size-of-value) for one parameter.

        :param-data:

            A blob of bytes from which each parameter key and value can be
            retrieved using the list of size couples stored in the previous
            field.

            Mandatory parameters comes first, then the advisory ones.

            Each parameter's key MUST be unique within the part.

:payload:

    payload is a series of `<chunksize><chunkdata>`.

    `chunksize` is an int32, `chunkdata` are plain bytes (as much as
    `chunksize` says)` The payload part is concluded by a zero size chunk.

    The current implementation always produces either zero or one chunk.
    This is an implementation limitation that will ultimately be lifted.

    `chunksize` can be negative to trigger special case processing. No such
    processing is in place yet.

Bundle processing
============================

Each part is processed in order using a "part handler". Handler are registered
for a certain part type.

The matching of a part to its handler is case insensitive. The case of the
part type is used to know if a part is mandatory or advisory. If the Part type
contains any uppercase char it is considered mandatory. When no handler is
known for a Mandatory part, the process is aborted and an exception is raised.
If the part is advisory and no handler is known, the part is ignored. When the
process is aborted, the full bundle is still read from the stream to keep the
channel usable. But none of the part read from an abort are processed. In the
future, dropping the stream may become an option for channel we do not care to
preserve.
"""

from __future__ import absolute_import, division

import collections
import errno
import os
import re
import string
import struct
import sys

from .i18n import _
from .node import (
    hex,
    short,
)
from . import (
    bookmarks,
    changegroup,
    encoding,
    error,
    obsolete,
    phases,
    pushkey,
    pycompat,
    requirements,
    scmutil,
    streamclone,
    tags,
    url,
    util,
)
from .utils import (
    stringutil,
    urlutil,
)
from .interfaces import repository

urlerr = util.urlerr
urlreq = util.urlreq

_pack = struct.pack
_unpack = struct.unpack

_fstreamparamsize = b'>i'
_fpartheadersize = b'>i'
_fparttypesize = b'>B'
_fpartid = b'>I'
_fpayloadsize = b'>i'
_fpartparamcount = b'>BB'

preferedchunksize = 32768

_parttypeforbidden = re.compile(b'[^a-zA-Z0-9_:-]')


def outdebug(ui, message):
    """debug regarding output stream (bundling)"""
    if ui.configbool(b'devel', b'bundle2.debug'):
        ui.debug(b'bundle2-output: %s\n' % message)


def indebug(ui, message):
    """debug on input stream (unbundling)"""
    if ui.configbool(b'devel', b'bundle2.debug'):
        ui.debug(b'bundle2-input: %s\n' % message)


def validateparttype(parttype):
    """raise ValueError if a parttype contains invalid character"""
    if _parttypeforbidden.search(parttype):
        raise ValueError(parttype)


def _makefpartparamsizes(nbparams):
    """return a struct format to read part parameter sizes

    The number parameters is variable so we need to build that format
    dynamically.
    """
    return b'>' + (b'BB' * nbparams)


parthandlermapping = {}


def parthandler(parttype, params=()):
    """decorator that register a function as a bundle2 part handler

    eg::

        @parthandler('myparttype', ('mandatory', 'param', 'handled'))
        def myparttypehandler(...):
            '''process a part of type "my part".'''
            ...
    """
    validateparttype(parttype)

    def _decorator(func):
        lparttype = parttype.lower()  # enforce lower case matching.
        assert lparttype not in parthandlermapping
        parthandlermapping[lparttype] = func
        func.params = frozenset(params)
        return func

    return _decorator


class unbundlerecords(object):
    """keep record of what happens during and unbundle

    New records are added using `records.add('cat', obj)`. Where 'cat' is a
    category of record and obj is an arbitrary object.

    `records['cat']` will return all entries of this category 'cat'.

    Iterating on the object itself will yield `('category', obj)` tuples
    for all entries.

    All iterations happens in chronological order.
    """

    def __init__(self):
        self._categories = {}
        self._sequences = []
        self._replies = {}

    def add(self, category, entry, inreplyto=None):
        """add a new record of a given category.

        The entry can then be retrieved in the list returned by
        self['category']."""
        self._categories.setdefault(category, []).append(entry)
        self._sequences.append((category, entry))
        if inreplyto is not None:
            self.getreplies(inreplyto).add(category, entry)

    def getreplies(self, partid):
        """get the records that are replies to a specific part"""
        return self._replies.setdefault(partid, unbundlerecords())

    def __getitem__(self, cat):
        return tuple(self._categories.get(cat, ()))

    def __iter__(self):
        return iter(self._sequences)

    def __len__(self):
        return len(self._sequences)

    def __nonzero__(self):
        return bool(self._sequences)

    __bool__ = __nonzero__


class bundleoperation(object):
    """an object that represents a single bundling process

    Its purpose is to carry unbundle-related objects and states.

    A new object should be created at the beginning of each bundle processing.
    The object is to be returned by the processing function.

    The object has very little content now it will ultimately contain:
    * an access to the repo the bundle is applied to,
    * a ui object,
    * a way to retrieve a transaction to add changes to the repo,
    * a way to record the result of processing each part,
    * a way to construct a bundle response when applicable.
    """

    def __init__(self, repo, transactiongetter, captureoutput=True, source=b''):
        self.repo = repo
        self.ui = repo.ui
        self.records = unbundlerecords()
        self.reply = None
        self.captureoutput = captureoutput
        self.hookargs = {}
        self._gettransaction = transactiongetter
        # carries value that can modify part behavior
        self.modes = {}
        self.source = source

    def gettransaction(self):
        transaction = self._gettransaction()

        if self.hookargs:
            # the ones added to the transaction supercede those added
            # to the operation.
            self.hookargs.update(transaction.hookargs)
            transaction.hookargs = self.hookargs

        # mark the hookargs as flushed.  further attempts to add to
        # hookargs will result in an abort.
        self.hookargs = None

        return transaction

    def addhookargs(self, hookargs):
        if self.hookargs is None:
            raise error.ProgrammingError(
                b'attempted to add hookargs to '
                b'operation after transaction started'
            )
        self.hookargs.update(hookargs)


class TransactionUnavailable(RuntimeError):
    pass


def _notransaction():
    """default method to get a transaction while processing a bundle

    Raise an exception to highlight the fact that no transaction was expected
    to be created"""
    raise TransactionUnavailable()


def applybundle(repo, unbundler, tr, source, url=None, **kwargs):
    # transform me into unbundler.apply() as soon as the freeze is lifted
    if isinstance(unbundler, unbundle20):
        tr.hookargs[b'bundle2'] = b'1'
        if source is not None and b'source' not in tr.hookargs:
            tr.hookargs[b'source'] = source
        if url is not None and b'url' not in tr.hookargs:
            tr.hookargs[b'url'] = url
        return processbundle(repo, unbundler, lambda: tr, source=source)
    else:
        # the transactiongetter won't be used, but we might as well set it
        op = bundleoperation(repo, lambda: tr, source=source)
        _processchangegroup(op, unbundler, tr, source, url, **kwargs)
        return op


class partiterator(object):
    def __init__(self, repo, op, unbundler):
        self.repo = repo
        self.op = op
        self.unbundler = unbundler
        self.iterator = None
        self.count = 0
        self.current = None

    def __enter__(self):
        def func():
            itr = enumerate(self.unbundler.iterparts(), 1)
            for count, p in itr:
                self.count = count
                self.current = p
                yield p
                p.consume()
                self.current = None

        self.iterator = func()
        return self.iterator

    def __exit__(self, type, exc, tb):
        if not self.iterator:
            return

        # Only gracefully abort in a normal exception situation. User aborts
        # like Ctrl+C throw a KeyboardInterrupt which is not a base Exception,
        # and should not gracefully cleanup.
        if isinstance(exc, Exception):
            # Any exceptions seeking to the end of the bundle at this point are
            # almost certainly related to the underlying stream being bad.
            # And, chances are that the exception we're handling is related to
            # getting in that bad state. So, we swallow the seeking error and
            # re-raise the original error.
            seekerror = False
            try:
                if self.current:
                    # consume the part content to not corrupt the stream.
                    self.current.consume()

                for part in self.iterator:
                    # consume the bundle content
                    part.consume()
            except Exception:
                seekerror = True

            # Small hack to let caller code distinguish exceptions from bundle2
            # processing from processing the old format. This is mostly needed
            # to handle different return codes to unbundle according to the type
            # of bundle. We should probably clean up or drop this return code
            # craziness in a future version.
            exc.duringunbundle2 = True
            salvaged = []
            replycaps = None
            if self.op.reply is not None:
                salvaged = self.op.reply.salvageoutput()
                replycaps = self.op.reply.capabilities
            exc._replycaps = replycaps
            exc._bundle2salvagedoutput = salvaged

            # Re-raising from a variable loses the original stack. So only use
            # that form if we need to.
            if seekerror:
                raise exc

        self.repo.ui.debug(
            b'bundle2-input-bundle: %i parts total\n' % self.count
        )


def processbundle(repo, unbundler, transactiongetter=None, op=None, source=b''):
    """This function process a bundle, apply effect to/from a repo

    It iterates over each part then searches for and uses the proper handling
    code to process the part. Parts are processed in order.

    Unknown Mandatory part will abort the process.

    It is temporarily possible to provide a prebuilt bundleoperation to the
    function. This is used to ensure output is properly propagated in case of
    an error during the unbundling. This output capturing part will likely be
    reworked and this ability will probably go away in the process.
    """
    if op is None:
        if transactiongetter is None:
            transactiongetter = _notransaction
        op = bundleoperation(repo, transactiongetter, source=source)
    # todo:
    # - replace this is a init function soon.
    # - exception catching
    unbundler.params
    if repo.ui.debugflag:
        msg = [b'bundle2-input-bundle:']
        if unbundler.params:
            msg.append(b' %i params' % len(unbundler.params))
        if op._gettransaction is None or op._gettransaction is _notransaction:
            msg.append(b' no-transaction')
        else:
            msg.append(b' with-transaction')
        msg.append(b'\n')
        repo.ui.debug(b''.join(msg))

    processparts(repo, op, unbundler)

    return op


def processparts(repo, op, unbundler):
    with partiterator(repo, op, unbundler) as parts:
        for part in parts:
            _processpart(op, part)


def _processchangegroup(op, cg, tr, source, url, **kwargs):
    ret = cg.apply(op.repo, tr, source, url, **kwargs)
    op.records.add(
        b'changegroup',
        {
            b'return': ret,
        },
    )
    return ret


def _gethandler(op, part):
    status = b'unknown'  # used by debug output
    try:
        handler = parthandlermapping.get(part.type)
        if handler is None:
            status = b'unsupported-type'
            raise error.BundleUnknownFeatureError(parttype=part.type)
        indebug(op.ui, b'found a handler for part %s' % part.type)
        unknownparams = part.mandatorykeys - handler.params
        if unknownparams:
            unknownparams = list(unknownparams)
            unknownparams.sort()
            status = b'unsupported-params (%s)' % b', '.join(unknownparams)
            raise error.BundleUnknownFeatureError(
                parttype=part.type, params=unknownparams
            )
        status = b'supported'
    except error.BundleUnknownFeatureError as exc:
        if part.mandatory:  # mandatory parts
            raise
        indebug(op.ui, b'ignoring unsupported advisory part %s' % exc)
        return  # skip to part processing
    finally:
        if op.ui.debugflag:
            msg = [b'bundle2-input-part: "%s"' % part.type]
            if not part.mandatory:
                msg.append(b' (advisory)')
            nbmp = len(part.mandatorykeys)
            nbap = len(part.params) - nbmp
            if nbmp or nbap:
                msg.append(b' (params:')
                if nbmp:
                    msg.append(b' %i mandatory' % nbmp)
                if nbap:
                    msg.append(b' %i advisory' % nbmp)
                msg.append(b')')
            msg.append(b' %s\n' % status)
            op.ui.debug(b''.join(msg))

    return handler


def _processpart(op, part):
    """process a single part from a bundle

    The part is guaranteed to have been fully consumed when the function exits
    (even if an exception is raised)."""
    handler = _gethandler(op, part)
    if handler is None:
        return

    # handler is called outside the above try block so that we don't
    # risk catching KeyErrors from anything other than the
    # parthandlermapping lookup (any KeyError raised by handler()
    # itself represents a defect of a different variety).
    output = None
    if op.captureoutput and op.reply is not None:
        op.ui.pushbuffer(error=True, subproc=True)
        output = b''
    try:
        handler(op, part)
    finally:
        if output is not None:
            output = op.ui.popbuffer()
        if output:
            outpart = op.reply.newpart(b'output', data=output, mandatory=False)
            outpart.addparam(
                b'in-reply-to', pycompat.bytestr(part.id), mandatory=False
            )


def decodecaps(blob):
    """decode a bundle2 caps bytes blob into a dictionary

    The blob is a list of capabilities (one per line)
    Capabilities may have values using a line of the form::

        capability=value1,value2,value3

    The values are always a list."""
    caps = {}
    for line in blob.splitlines():
        if not line:
            continue
        if b'=' not in line:
            key, vals = line, ()
        else:
            key, vals = line.split(b'=', 1)
            vals = vals.split(b',')
        key = urlreq.unquote(key)
        vals = [urlreq.unquote(v) for v in vals]
        caps[key] = vals
    return caps


def encodecaps(caps):
    """encode a bundle2 caps dictionary into a bytes blob"""
    chunks = []
    for ca in sorted(caps):
        vals = caps[ca]
        ca = urlreq.quote(ca)
        vals = [urlreq.quote(v) for v in vals]
        if vals:
            ca = b"%s=%s" % (ca, b','.join(vals))
        chunks.append(ca)
    return b'\n'.join(chunks)


bundletypes = {
    b"": (b"", b'UN'),  # only when using unbundle on ssh and old http servers
    # since the unification ssh accepts a header but there
    # is no capability signaling it.
    b"HG20": (),  # special-cased below
    b"HG10UN": (b"HG10UN", b'UN'),
    b"HG10BZ": (b"HG10", b'BZ'),
    b"HG10GZ": (b"HG10GZ", b'GZ'),
}

# hgweb uses this list to communicate its preferred type
bundlepriority = [b'HG10GZ', b'HG10BZ', b'HG10UN']


class bundle20(object):
    """represent an outgoing bundle2 container

    Use the `addparam` method to add stream level parameter. and `newpart` to
    populate it. Then call `getchunks` to retrieve all the binary chunks of
    data that compose the bundle2 container."""

    _magicstring = b'HG20'

    def __init__(self, ui, capabilities=()):
        self.ui = ui
        self._params = []
        self._parts = []
        self.capabilities = dict(capabilities)
        self._compengine = util.compengines.forbundletype(b'UN')
        self._compopts = None
        # If compression is being handled by a consumer of the raw
        # data (e.g. the wire protocol), unsetting this flag tells
        # consumers that the bundle is best left uncompressed.
        self.prefercompressed = True

    def setcompression(self, alg, compopts=None):
        """setup core part compression to <alg>"""
        if alg in (None, b'UN'):
            return
        assert not any(n.lower() == b'compression' for n, v in self._params)
        self.addparam(b'Compression', alg)
        self._compengine = util.compengines.forbundletype(alg)
        self._compopts = compopts

    @property
    def nbparts(self):
        """total number of parts added to the bundler"""
        return len(self._parts)

    # methods used to defines the bundle2 content
    def addparam(self, name, value=None):
        """add a stream level parameter"""
        if not name:
            raise error.ProgrammingError(b'empty parameter name')
        if name[0:1] not in pycompat.bytestr(
            string.ascii_letters  # pytype: disable=wrong-arg-types
        ):
            raise error.ProgrammingError(
                b'non letter first character: %s' % name
            )
        self._params.append((name, value))

    def addpart(self, part):
        """add a new part to the bundle2 container

        Parts contains the actual applicative payload."""
        assert part.id is None
        part.id = len(self._parts)  # very cheap counter
        self._parts.append(part)

    def newpart(self, typeid, *args, **kwargs):
        """create a new part and add it to the containers

        As the part is directly added to the containers. For now, this means
        that any failure to properly initialize the part after calling
        ``newpart`` should result in a failure of the whole bundling process.

        You can still fall back to manually create and add if you need better
        control."""
        part = bundlepart(typeid, *args, **kwargs)
        self.addpart(part)
        return part

    # methods used to generate the bundle2 stream
    def getchunks(self):
        if self.ui.debugflag:
            msg = [b'bundle2-output-bundle: "%s",' % self._magicstring]
            if self._params:
                msg.append(b' (%i params)' % len(self._params))
            msg.append(b' %i parts total\n' % len(self._parts))
            self.ui.debug(b''.join(msg))
        outdebug(self.ui, b'start emission of %s stream' % self._magicstring)
        yield self._magicstring
        param = self._paramchunk()
        outdebug(self.ui, b'bundle parameter: %s' % param)
        yield _pack(_fstreamparamsize, len(param))
        if param:
            yield param
        for chunk in self._compengine.compressstream(
            self._getcorechunk(), self._compopts
        ):
            yield chunk

    def _paramchunk(self):
        """return a encoded version of all stream parameters"""
        blocks = []
        for par, value in self._params:
            par = urlreq.quote(par)
            if value is not None:
                value = urlreq.quote(value)
                par = b'%s=%s' % (par, value)
            blocks.append(par)
        return b' '.join(blocks)

    def _getcorechunk(self):
        """yield chunk for the core part of the bundle

        (all but headers and parameters)"""
        outdebug(self.ui, b'start of parts')
        for part in self._parts:
            outdebug(self.ui, b'bundle part: "%s"' % part.type)
            for chunk in part.getchunks(ui=self.ui):
                yield chunk
        outdebug(self.ui, b'end of bundle')
        yield _pack(_fpartheadersize, 0)

    def salvageoutput(self):
        """return a list with a copy of all output parts in the bundle

        This is meant to be used during error handling to make sure we preserve
        server output"""
        salvaged = []
        for part in self._parts:
            if part.type.startswith(b'output'):
                salvaged.append(part.copy())
        return salvaged


class unpackermixin(object):
    """A mixin to extract bytes and struct data from a stream"""

    def __init__(self, fp):
        self._fp = fp

    def _unpack(self, format):
        """unpack this struct format from the stream

        This method is meant for internal usage by the bundle2 protocol only.
        They directly manipulate the low level stream including bundle2 level
        instruction.

        Do not use it to implement higher-level logic or methods."""
        data = self._readexact(struct.calcsize(format))
        return _unpack(format, data)

    def _readexact(self, size):
        """read exactly <size> bytes from the stream

        This method is meant for internal usage by the bundle2 protocol only.
        They directly manipulate the low level stream including bundle2 level
        instruction.

        Do not use it to implement higher-level logic or methods."""
        return changegroup.readexactly(self._fp, size)


def getunbundler(ui, fp, magicstring=None):
    """return a valid unbundler object for a given magicstring"""
    if magicstring is None:
        magicstring = changegroup.readexactly(fp, 4)
    magic, version = magicstring[0:2], magicstring[2:4]
    if magic != b'HG':
        ui.debug(
            b"error: invalid magic: %r (version %r), should be 'HG'\n"
            % (magic, version)
        )
        raise error.Abort(_(b'not a Mercurial bundle'))
    unbundlerclass = formatmap.get(version)
    if unbundlerclass is None:
        raise error.Abort(_(b'unknown bundle version %s') % version)
    unbundler = unbundlerclass(ui, fp)
    indebug(ui, b'start processing of %s stream' % magicstring)
    return unbundler


class unbundle20(unpackermixin):
    """interpret a bundle2 stream

    This class is fed with a binary stream and yields parts through its
    `iterparts` methods."""

    _magicstring = b'HG20'

    def __init__(self, ui, fp):
        """If header is specified, we do not read it out of the stream."""
        self.ui = ui
        self._compengine = util.compengines.forbundletype(b'UN')
        self._compressed = None
        super(unbundle20, self).__init__(fp)

    @util.propertycache
    def params(self):
        """dictionary of stream level parameters"""
        indebug(self.ui, b'reading bundle2 stream parameters')
        params = {}
        paramssize = self._unpack(_fstreamparamsize)[0]
        if paramssize < 0:
            raise error.BundleValueError(
                b'negative bundle param size: %i' % paramssize
            )
        if paramssize:
            params = self._readexact(paramssize)
            params = self._processallparams(params)
        return params

    def _processallparams(self, paramsblock):
        """ """
        params = util.sortdict()
        for p in paramsblock.split(b' '):
            p = p.split(b'=', 1)
            p = [urlreq.unquote(i) for i in p]
            if len(p) < 2:
                p.append(None)
            self._processparam(*p)
            params[p[0]] = p[1]
        return params

    def _processparam(self, name, value):
        """process a parameter, applying its effect if needed

        Parameter starting with a lower case letter are advisory and will be
        ignored when unknown.  Those starting with an upper case letter are
        mandatory and will this function will raise a KeyError when unknown.

        Note: no option are currently supported. Any input will be either
              ignored or failing.
        """
        if not name:
            raise ValueError('empty parameter name')
        if name[0:1] not in pycompat.bytestr(
            string.ascii_letters  # pytype: disable=wrong-arg-types
        ):
            raise ValueError('non letter first character: %s' % name)
        try:
            handler = b2streamparamsmap[name.lower()]
        except KeyError:
            if name[0:1].islower():
                indebug(self.ui, b"ignoring unknown parameter %s" % name)
            else:
                raise error.BundleUnknownFeatureError(params=(name,))
        else:
            handler(self, name, value)

    def _forwardchunks(self):
        """utility to transfer a bundle2 as binary

        This is made necessary by the fact the 'getbundle' command over 'ssh'
        have no way to know then the reply end, relying on the bundle to be
        interpreted to know its end. This is terrible and we are sorry, but we
        needed to move forward to get general delta enabled.
        """
        yield self._magicstring
        assert 'params' not in vars(self)
        paramssize = self._unpack(_fstreamparamsize)[0]
        if paramssize < 0:
            raise error.BundleValueError(
                b'negative bundle param size: %i' % paramssize
            )
        if paramssize:
            params = self._readexact(paramssize)
            self._processallparams(params)
            # The payload itself is decompressed below, so drop
            # the compression parameter passed down to compensate.
            outparams = []
            for p in params.split(b' '):
                k, v = p.split(b'=', 1)
                if k.lower() != b'compression':
                    outparams.append(p)
            outparams = b' '.join(outparams)
            yield _pack(_fstreamparamsize, len(outparams))
            yield outparams
        else:
            yield _pack(_fstreamparamsize, paramssize)
        # From there, payload might need to be decompressed
        self._fp = self._compengine.decompressorreader(self._fp)
        emptycount = 0
        while emptycount < 2:
            # so we can brainlessly loop
            assert _fpartheadersize == _fpayloadsize
            size = self._unpack(_fpartheadersize)[0]
            yield _pack(_fpartheadersize, size)
            if size:
                emptycount = 0
            else:
                emptycount += 1
                continue
            if size == flaginterrupt:
                continue
            elif size < 0:
                raise error.BundleValueError(b'negative chunk size: %i')
            yield self._readexact(size)

    def iterparts(self, seekable=False):
        """yield all parts contained in the stream"""
        cls = seekableunbundlepart if seekable else unbundlepart
        # make sure param have been loaded
        self.params
        # From there, payload need to be decompressed
        self._fp = self._compengine.decompressorreader(self._fp)
        indebug(self.ui, b'start extraction of bundle2 parts')
        headerblock = self._readpartheader()
        while headerblock is not None:
            part = cls(self.ui, headerblock, self._fp)
            yield part
            # Ensure part is fully consumed so we can start reading the next
            # part.
            part.consume()

            headerblock = self._readpartheader()
        indebug(self.ui, b'end of bundle2 stream')

    def _readpartheader(self):
        """reads a part header size and return the bytes blob

        returns None if empty"""
        headersize = self._unpack(_fpartheadersize)[0]
        if headersize < 0:
            raise error.BundleValueError(
                b'negative part header size: %i' % headersize
            )
        indebug(self.ui, b'part header size: %i' % headersize)
        if headersize:
            return self._readexact(headersize)
        return None

    def compressed(self):
        self.params  # load params
        return self._compressed

    def close(self):
        """close underlying file"""
        if util.safehasattr(self._fp, 'close'):
            return self._fp.close()


formatmap = {b'20': unbundle20}

b2streamparamsmap = {}


def b2streamparamhandler(name):
    """register a handler for a stream level parameter"""

    def decorator(func):
        assert name not in formatmap
        b2streamparamsmap[name] = func
        return func

    return decorator


@b2streamparamhandler(b'compression')
def processcompression(unbundler, param, value):
    """read compression parameter and install payload decompression"""
    if value not in util.compengines.supportedbundletypes:
        raise error.BundleUnknownFeatureError(params=(param,), values=(value,))
    unbundler._compengine = util.compengines.forbundletype(value)
    if value is not None:
        unbundler._compressed = True


class bundlepart(object):
    """A bundle2 part contains application level payload

    The part `type` is used to route the part to the application level
    handler.

    The part payload is contained in ``part.data``. It could be raw bytes or a
    generator of byte chunks.

    You can add parameters to the part using the ``addparam`` method.
    Parameters can be either mandatory (default) or advisory. Remote side
    should be able to safely ignore the advisory ones.

    Both data and parameters cannot be modified after the generation has begun.
    """

    def __init__(
        self,
        parttype,
        mandatoryparams=(),
        advisoryparams=(),
        data=b'',
        mandatory=True,
    ):
        validateparttype(parttype)
        self.id = None
        self.type = parttype
        self._data = data
        self._mandatoryparams = list(mandatoryparams)
        self._advisoryparams = list(advisoryparams)
        # checking for duplicated entries
        self._seenparams = set()
        for pname, __ in self._mandatoryparams + self._advisoryparams:
            if pname in self._seenparams:
                raise error.ProgrammingError(b'duplicated params: %s' % pname)
            self._seenparams.add(pname)
        # status of the part's generation:
        # - None: not started,
        # - False: currently generated,
        # - True: generation done.
        self._generated = None
        self.mandatory = mandatory

    def __repr__(self):
        cls = "%s.%s" % (self.__class__.__module__, self.__class__.__name__)
        return '<%s object at %x; id: %s; type: %s; mandatory: %s>' % (
            cls,
            id(self),
            self.id,
            self.type,
            self.mandatory,
        )

    def copy(self):
        """return a copy of the part

        The new part have the very same content but no partid assigned yet.
        Parts with generated data cannot be copied."""
        assert not util.safehasattr(self.data, 'next')
        return self.__class__(
            self.type,
            self._mandatoryparams,
            self._advisoryparams,
            self._data,
            self.mandatory,
        )

    # methods used to defines the part content
    @property
    def data(self):
        return self._data

    @data.setter
    def data(self, data):
        if self._generated is not None:
            raise error.ReadOnlyPartError(b'part is being generated')
        self._data = data

    @property
    def mandatoryparams(self):
        # make it an immutable tuple to force people through ``addparam``
        return tuple(self._mandatoryparams)

    @property
    def advisoryparams(self):
        # make it an immutable tuple to force people through ``addparam``
        return tuple(self._advisoryparams)

    def addparam(self, name, value=b'', mandatory=True):
        """add a parameter to the part

        If 'mandatory' is set to True, the remote handler must claim support
        for this parameter or the unbundling will be aborted.

        The 'name' and 'value' cannot exceed 255 bytes each.
        """
        if self._generated is not None:
            raise error.ReadOnlyPartError(b'part is being generated')
        if name in self._seenparams:
            raise ValueError(b'duplicated params: %s' % name)
        self._seenparams.add(name)
        params = self._advisoryparams
        if mandatory:
            params = self._mandatoryparams
        params.append((name, value))

    # methods used to generates the bundle2 stream
    def getchunks(self, ui):
        if self._generated is not None:
            raise error.ProgrammingError(b'part can only be consumed once')
        self._generated = False

        if ui.debugflag:
            msg = [b'bundle2-output-part: "%s"' % self.type]
            if not self.mandatory:
                msg.append(b' (advisory)')
            nbmp = len(self.mandatoryparams)
            nbap = len(self.advisoryparams)
            if nbmp or nbap:
                msg.append(b' (params:')
                if nbmp:
                    msg.append(b' %i mandatory' % nbmp)
                if nbap:
                    msg.append(b' %i advisory' % nbmp)
                msg.append(b')')
            if not self.data:
                msg.append(b' empty payload')
            elif util.safehasattr(self.data, 'next') or util.safehasattr(
                self.data, b'__next__'
            ):
                msg.append(b' streamed payload')
            else:
                msg.append(b' %i bytes payload' % len(self.data))
            msg.append(b'\n')
            ui.debug(b''.join(msg))

        #### header
        if self.mandatory:
            parttype = self.type.upper()
        else:
            parttype = self.type.lower()
        outdebug(ui, b'part %s: "%s"' % (pycompat.bytestr(self.id), parttype))
        ## parttype
        header = [
            _pack(_fparttypesize, len(parttype)),
            parttype,
            _pack(_fpartid, self.id),
        ]
        ## parameters
        # count
        manpar = self.mandatoryparams
        advpar = self.advisoryparams
        header.append(_pack(_fpartparamcount, len(manpar), len(advpar)))
        # size
        parsizes = []
        for key, value in manpar:
            parsizes.append(len(key))
            parsizes.append(len(value))
        for key, value in advpar:
            parsizes.append(len(key))
            parsizes.append(len(value))
        paramsizes = _pack(_makefpartparamsizes(len(parsizes) // 2), *parsizes)
        header.append(paramsizes)
        # key, value
        for key, value in manpar:
            header.append(key)
            header.append(value)
        for key, value in advpar:
            header.append(key)
            header.append(value)
        ## finalize header
        try:
            headerchunk = b''.join(header)
        except TypeError:
            raise TypeError(
                'Found a non-bytes trying to '
                'build bundle part header: %r' % header
            )
        outdebug(ui, b'header chunk size: %i' % len(headerchunk))
        yield _pack(_fpartheadersize, len(headerchunk))
        yield headerchunk
        ## payload
        try:
            for chunk in self._payloadchunks():
                outdebug(ui, b'payload chunk size: %i' % len(chunk))
                yield _pack(_fpayloadsize, len(chunk))
                yield chunk
        except GeneratorExit:
            # GeneratorExit means that nobody is listening for our
            # results anyway, so just bail quickly rather than trying
            # to produce an error part.
            ui.debug(b'bundle2-generatorexit\n')
            raise
        except BaseException as exc:
            bexc = stringutil.forcebytestr(exc)
            # backup exception data for later
            ui.debug(
                b'bundle2-input-stream-interrupt: encoding exception %s' % bexc
            )
            tb = sys.exc_info()[2]
            msg = b'unexpected error: %s' % bexc
            interpart = bundlepart(
                b'error:abort', [(b'message', msg)], mandatory=False
            )
            interpart.id = 0
            yield _pack(_fpayloadsize, -1)
            for chunk in interpart.getchunks(ui=ui):
                yield chunk
            outdebug(ui, b'closing payload chunk')
            # abort current part payload
            yield _pack(_fpayloadsize, 0)
            pycompat.raisewithtb(exc, tb)
        # end of payload
        outdebug(ui, b'closing payload chunk')
        yield _pack(_fpayloadsize, 0)
        self._generated = True

    def _payloadchunks(self):
        """yield chunks of a the part payload

        Exists to handle the different methods to provide data to a part."""
        # we only support fixed size data now.
        # This will be improved in the future.
        if util.safehasattr(self.data, 'next') or util.safehasattr(
            self.data, b'__next__'
        ):
            buff = util.chunkbuffer(self.data)
            chunk = buff.read(preferedchunksize)
            while chunk:
                yield chunk
                chunk = buff.read(preferedchunksize)
        elif len(self.data):
            yield self.data


flaginterrupt = -1


class interrupthandler(unpackermixin):
    """read one part and process it with restricted capability

    This allows to transmit exception raised on the producer size during part
    iteration while the consumer is reading a part.

    Part processed in this manner only have access to a ui object,"""

    def __init__(self, ui, fp):
        super(interrupthandler, self).__init__(fp)
        self.ui = ui

    def _readpartheader(self):
        """reads a part header size and return the bytes blob

        returns None if empty"""
        headersize = self._unpack(_fpartheadersize)[0]
        if headersize < 0:
            raise error.BundleValueError(
                b'negative part header size: %i' % headersize
            )
        indebug(self.ui, b'part header size: %i\n' % headersize)
        if headersize:
            return self._readexact(headersize)
        return None

    def __call__(self):

        self.ui.debug(
            b'bundle2-input-stream-interrupt: opening out of band context\n'
        )
        indebug(self.ui, b'bundle2 stream interruption, looking for a part.')
        headerblock = self._readpartheader()
        if headerblock is None:
            indebug(self.ui, b'no part found during interruption.')
            return
        part = unbundlepart(self.ui, headerblock, self._fp)
        op = interruptoperation(self.ui)
        hardabort = False
        try:
            _processpart(op, part)
        except (SystemExit, KeyboardInterrupt):
            hardabort = True
            raise
        finally:
            if not hardabort:
                part.consume()
        self.ui.debug(
            b'bundle2-input-stream-interrupt: closing out of band context\n'
        )


class interruptoperation(object):
    """A limited operation to be use by part handler during interruption

    It only have access to an ui object.
    """

    def __init__(self, ui):
        self.ui = ui
        self.reply = None
        self.captureoutput = False

    @property
    def repo(self):
        raise error.ProgrammingError(b'no repo access from stream interruption')

    def gettransaction(self):
        raise TransactionUnavailable(b'no repo access from stream interruption')


def decodepayloadchunks(ui, fh):
    """Reads bundle2 part payload data into chunks.

    Part payload data consists of framed chunks. This function takes
    a file handle and emits those chunks.
    """
    dolog = ui.configbool(b'devel', b'bundle2.debug')
    debug = ui.debug

    headerstruct = struct.Struct(_fpayloadsize)
    headersize = headerstruct.size
    unpack = headerstruct.unpack

    readexactly = changegroup.readexactly
    read = fh.read

    chunksize = unpack(readexactly(fh, headersize))[0]
    indebug(ui, b'payload chunk size: %i' % chunksize)

    # changegroup.readexactly() is inlined below for performance.
    while chunksize:
        if chunksize >= 0:
            s = read(chunksize)
            if len(s) < chunksize:
                raise error.Abort(
                    _(
                        b'stream ended unexpectedly '
                        b' (got %d bytes, expected %d)'
                    )
                    % (len(s), chunksize)
                )

            yield s
        elif chunksize == flaginterrupt:
            # Interrupt "signal" detected. The regular stream is interrupted
            # and a bundle2 part follows. Consume it.
            interrupthandler(ui, fh)()
        else:
            raise error.BundleValueError(
                b'negative payload chunk size: %s' % chunksize
            )

        s = read(headersize)
        if len(s) < headersize:
            raise error.Abort(
                _(b'stream ended unexpectedly  (got %d bytes, expected %d)')
                % (len(s), chunksize)
            )

        chunksize = unpack(s)[0]

        # indebug() inlined for performance.
        if dolog:
            debug(b'bundle2-input: payload chunk size: %i\n' % chunksize)


class unbundlepart(unpackermixin):
    """a bundle part read from a bundle"""

    def __init__(self, ui, header, fp):
        super(unbundlepart, self).__init__(fp)
        self._seekable = util.safehasattr(fp, 'seek') and util.safehasattr(
            fp, b'tell'
        )
        self.ui = ui
        # unbundle state attr
        self._headerdata = header
        self._headeroffset = 0
        self._initialized = False
        self.consumed = False
        # part data
        self.id = None
        self.type = None
        self.mandatoryparams = None
        self.advisoryparams = None
        self.params = None
        self.mandatorykeys = ()
        self._readheader()
        self._mandatory = None
        self._pos = 0

    def _fromheader(self, size):
        """return the next <size> byte from the header"""
        offset = self._headeroffset
        data = self._headerdata[offset : (offset + size)]
        self._headeroffset = offset + size
        return data

    def _unpackheader(self, format):
        """read given format from header

        This automatically compute the size of the format to read."""
        data = self._fromheader(struct.calcsize(format))
        return _unpack(format, data)

    def _initparams(self, mandatoryparams, advisoryparams):
        """internal function to setup all logic related parameters"""
        # make it read only to prevent people touching it by mistake.
        self.mandatoryparams = tuple(mandatoryparams)
        self.advisoryparams = tuple(advisoryparams)
        # user friendly UI
        self.params = util.sortdict(self.mandatoryparams)
        self.params.update(self.advisoryparams)
        self.mandatorykeys = frozenset(p[0] for p in mandatoryparams)

    def _readheader(self):
        """read the header and setup the object"""
        typesize = self._unpackheader(_fparttypesize)[0]
        self.type = self._fromheader(typesize)
        indebug(self.ui, b'part type: "%s"' % self.type)
        self.id = self._unpackheader(_fpartid)[0]
        indebug(self.ui, b'part id: "%s"' % pycompat.bytestr(self.id))
        # extract mandatory bit from type
        self.mandatory = self.type != self.type.lower()
        self.type = self.type.lower()
        ## reading parameters
        # param count
        mancount, advcount = self._unpackheader(_fpartparamcount)
        indebug(self.ui, b'part parameters: %i' % (mancount + advcount))
        # param size
        fparamsizes = _makefpartparamsizes(mancount + advcount)
        paramsizes = self._unpackheader(fparamsizes)
        # make it a list of couple again
        paramsizes = list(zip(paramsizes[::2], paramsizes[1::2]))
        # split mandatory from advisory
        mansizes = paramsizes[:mancount]
        advsizes = paramsizes[mancount:]
        # retrieve param value
        manparams = []
        for key, value in mansizes:
            manparams.append((self._fromheader(key), self._fromheader(value)))
        advparams = []
        for key, value in advsizes:
            advparams.append((self._fromheader(key), self._fromheader(value)))
        self._initparams(manparams, advparams)
        ## part payload
        self._payloadstream = util.chunkbuffer(self._payloadchunks())
        # we read the data, tell it
        self._initialized = True

    def _payloadchunks(self):
        """Generator of decoded chunks in the payload."""
        return decodepayloadchunks(self.ui, self._fp)

    def consume(self):
        """Read the part payload until completion.

        By consuming the part data, the underlying stream read offset will
        be advanced to the next part (or end of stream).
        """
        if self.consumed:
            return

        chunk = self.read(32768)
        while chunk:
            self._pos += len(chunk)
            chunk = self.read(32768)

    def read(self, size=None):
        """read payload data"""
        if not self._initialized:
            self._readheader()
        if size is None:
            data = self._payloadstream.read()
        else:
            data = self._payloadstream.read(size)
        self._pos += len(data)
        if size is None or len(data) < size:
            if not self.consumed and self._pos:
                self.ui.debug(
                    b'bundle2-input-part: total payload size %i\n' % self._pos
                )
            self.consumed = True
        return data


class seekableunbundlepart(unbundlepart):
    """A bundle2 part in a bundle that is seekable.

    Regular ``unbundlepart`` instances can only be read once. This class
    extends ``unbundlepart`` to enable bi-directional seeking within the
    part.

    Bundle2 part data consists of framed chunks. Offsets when seeking
    refer to the decoded data, not the offsets in the underlying bundle2
    stream.

    To facilitate quickly seeking within the decoded data, instances of this
    class maintain a mapping between offsets in the underlying stream and
    the decoded payload. This mapping will consume memory in proportion
    to the number of chunks within the payload (which almost certainly
    increases in proportion with the size of the part).
    """

    def __init__(self, ui, header, fp):
        # (payload, file) offsets for chunk starts.
        self._chunkindex = []

        super(seekableunbundlepart, self).__init__(ui, header, fp)

    def _payloadchunks(self, chunknum=0):
        '''seek to specified chunk and start yielding data'''
        if len(self._chunkindex) == 0:
            assert chunknum == 0, b'Must start with chunk 0'
            self._chunkindex.append((0, self._tellfp()))
        else:
            assert chunknum < len(self._chunkindex), (
                b'Unknown chunk %d' % chunknum
            )
            self._seekfp(self._chunkindex[chunknum][1])

        pos = self._chunkindex[chunknum][0]

        for chunk in decodepayloadchunks(self.ui, self._fp):
            chunknum += 1
            pos += len(chunk)
            if chunknum == len(self._chunkindex):
                self._chunkindex.append((pos, self._tellfp()))

            yield chunk

    def _findchunk(self, pos):
        '''for a given payload position, return a chunk number and offset'''
        for chunk, (ppos, fpos) in enumerate(self._chunkindex):
            if ppos == pos:
                return chunk, 0
            elif ppos > pos:
                return chunk - 1, pos - self._chunkindex[chunk - 1][0]
        raise ValueError(b'Unknown chunk')

    def tell(self):
        return self._pos

    def seek(self, offset, whence=os.SEEK_SET):
        if whence == os.SEEK_SET:
            newpos = offset
        elif whence == os.SEEK_CUR:
            newpos = self._pos + offset
        elif whence == os.SEEK_END:
            if not self.consumed:
                # Can't use self.consume() here because it advances self._pos.
                chunk = self.read(32768)
                while chunk:
                    chunk = self.read(32768)
            newpos = self._chunkindex[-1][0] - offset
        else:
            raise ValueError(b'Unknown whence value: %r' % (whence,))

        if newpos > self._chunkindex[-1][0] and not self.consumed:
            # Can't use self.consume() here because it advances self._pos.
            chunk = self.read(32768)
            while chunk:
                chunk = self.read(32668)

        if not 0 <= newpos <= self._chunkindex[-1][0]:
            raise ValueError(b'Offset out of range')

        if self._pos != newpos:
            chunk, internaloffset = self._findchunk(newpos)
            self._payloadstream = util.chunkbuffer(self._payloadchunks(chunk))
            adjust = self.read(internaloffset)
            if len(adjust) != internaloffset:
                raise error.Abort(_(b'Seek failed\n'))
            self._pos = newpos

    def _seekfp(self, offset, whence=0):
        """move the underlying file pointer

        This method is meant for internal usage by the bundle2 protocol only.
        They directly manipulate the low level stream including bundle2 level
        instruction.

        Do not use it to implement higher-level logic or methods."""
        if self._seekable:
            return self._fp.seek(offset, whence)
        else:
            raise NotImplementedError(_(b'File pointer is not seekable'))

    def _tellfp(self):
        """return the file offset, or None if file is not seekable

        This method is meant for internal usage by the bundle2 protocol only.
        They directly manipulate the low level stream including bundle2 level
        instruction.

        Do not use it to implement higher-level logic or methods."""
        if self._seekable:
            try:
                return self._fp.tell()
            except IOError as e:
                if e.errno == errno.ESPIPE:
                    self._seekable = False
                else:
                    raise
        return None


# These are only the static capabilities.
# Check the 'getrepocaps' function for the rest.
capabilities = {
    b'HG20': (),
    b'bookmarks': (),
    b'error': (b'abort', b'unsupportedcontent', b'pushraced', b'pushkey'),
    b'listkeys': (),
    b'pushkey': (),
    b'digests': tuple(sorted(util.DIGESTS.keys())),
    b'remote-changegroup': (b'http', b'https'),
    b'hgtagsfnodes': (),
    b'phases': (b'heads',),
    b'stream': (b'v2',),
}


def getrepocaps(repo, allowpushback=False, role=None):
    """return the bundle2 capabilities for a given repo

    Exists to allow extensions (like evolution) to mutate the capabilities.

    The returned value is used for servers advertising their capabilities as
    well as clients advertising their capabilities to servers as part of
    bundle2 requests. The ``role`` argument specifies which is which.
    """
    if role not in (b'client', b'server'):
        raise error.ProgrammingError(b'role argument must be client or server')

    caps = capabilities.copy()
    caps[b'changegroup'] = tuple(
        sorted(changegroup.supportedincomingversions(repo))
    )
    if obsolete.isenabled(repo, obsolete.exchangeopt):
        supportedformat = tuple(b'V%i' % v for v in obsolete.formats)
        caps[b'obsmarkers'] = supportedformat
    if allowpushback:
        caps[b'pushback'] = ()
    cpmode = repo.ui.config(b'server', b'concurrent-push-mode')
    if cpmode == b'check-related':
        caps[b'checkheads'] = (b'related',)
    if b'phases' in repo.ui.configlist(b'devel', b'legacy.exchange'):
        caps.pop(b'phases')

    # Don't advertise stream clone support in server mode if not configured.
    if role == b'server':
        streamsupported = repo.ui.configbool(
            b'server', b'uncompressed', untrusted=True
        )
        featuresupported = repo.ui.configbool(b'server', b'bundle2.stream')

        if not streamsupported or not featuresupported:
            caps.pop(b'stream')
    # Else always advertise support on client, because payload support
    # should always be advertised.

    # b'rev-branch-cache is no longer advertised, but still supported
    # for legacy clients.

    return caps


def bundle2caps(remote):
    """return the bundle capabilities of a peer as dict"""
    raw = remote.capable(b'bundle2')
    if not raw and raw != b'':
        return {}
    capsblob = urlreq.unquote(remote.capable(b'bundle2'))
    return decodecaps(capsblob)


def obsmarkersversion(caps):
    """extract the list of supported obsmarkers versions from a bundle2caps dict"""
    obscaps = caps.get(b'obsmarkers', ())
    return [int(c[1:]) for c in obscaps if c.startswith(b'V')]


def writenewbundle(
    ui,
    repo,
    source,
    filename,
    bundletype,
    outgoing,
    opts,
    vfs=None,
    compression=None,
    compopts=None,
):
    if bundletype.startswith(b'HG10'):
        cg = changegroup.makechangegroup(repo, outgoing, b'01', source)
        return writebundle(
            ui,
            cg,
            filename,
            bundletype,
            vfs=vfs,
            compression=compression,
            compopts=compopts,
        )
    elif not bundletype.startswith(b'HG20'):
        raise error.ProgrammingError(b'unknown bundle type: %s' % bundletype)

    caps = {}
    if b'obsolescence' in opts:
        caps[b'obsmarkers'] = (b'V1',)
    bundle = bundle20(ui, caps)
    bundle.setcompression(compression, compopts)
    _addpartsfromopts(ui, repo, bundle, source, outgoing, opts)
    chunkiter = bundle.getchunks()

    return changegroup.writechunks(ui, chunkiter, filename, vfs=vfs)


def _addpartsfromopts(ui, repo, bundler, source, outgoing, opts):
    # We should eventually reconcile this logic with the one behind
    # 'exchange.getbundle2partsgenerator'.
    #
    # The type of input from 'getbundle' and 'writenewbundle' are a bit
    # different right now. So we keep them separated for now for the sake of
    # simplicity.

    # we might not always want a changegroup in such bundle, for example in
    # stream bundles
    if opts.get(b'changegroup', True):
        cgversion = opts.get(b'cg.version')
        if cgversion is None:
            cgversion = changegroup.safeversion(repo)
        cg = changegroup.makechangegroup(repo, outgoing, cgversion, source)
        part = bundler.newpart(b'changegroup', data=cg.getchunks())
        part.addparam(b'version', cg.version)
        if b'clcount' in cg.extras:
            part.addparam(
                b'nbchanges', b'%d' % cg.extras[b'clcount'], mandatory=False
            )
        if opts.get(b'phases') and repo.revs(
            b'%ln and secret()', outgoing.ancestorsof
        ):
            part.addparam(
                b'targetphase', b'%d' % phases.secret, mandatory=False
            )
    if repository.REPO_FEATURE_SIDE_DATA in repo.features:
        part.addparam(b'exp-sidedata', b'1')

    if opts.get(b'streamv2', False):
        addpartbundlestream2(bundler, repo, stream=True)

    if opts.get(b'tagsfnodescache', True):
        addparttagsfnodescache(repo, bundler, outgoing)

    if opts.get(b'revbranchcache', True):
        addpartrevbranchcache(repo, bundler, outgoing)

    if opts.get(b'obsolescence', False):
        obsmarkers = repo.obsstore.relevantmarkers(outgoing.missing)
        buildobsmarkerspart(
            bundler,
            obsmarkers,
            mandatory=opts.get(b'obsolescence-mandatory', True),
        )

    if opts.get(b'phases', False):
        headsbyphase = phases.subsetphaseheads(repo, outgoing.missing)
        phasedata = phases.binaryencode(headsbyphase)
        bundler.newpart(b'phase-heads', data=phasedata)


def addparttagsfnodescache(repo, bundler, outgoing):
    # we include the tags fnode cache for the bundle changeset
    # (as an optional parts)
    cache = tags.hgtagsfnodescache(repo.unfiltered())
    chunks = []

    # .hgtags fnodes are only relevant for head changesets. While we could
    # transfer values for all known nodes, there will likely be little to
    # no benefit.
    #
    # We don't bother using a generator to produce output data because
    # a) we only have 40 bytes per head and even esoteric numbers of heads
    # consume little memory (1M heads is 40MB) b) we don't want to send the
    # part if we don't have entries and knowing if we have entries requires
    # cache lookups.
    for node in outgoing.ancestorsof:
        # Don't compute missing, as this may slow down serving.
        fnode = cache.getfnode(node, computemissing=False)
        if fnode:
            chunks.extend([node, fnode])

    if chunks:
        bundler.newpart(b'hgtagsfnodes', data=b''.join(chunks))


def addpartrevbranchcache(repo, bundler, outgoing):
    # we include the rev branch cache for the bundle changeset
    # (as an optional parts)
    cache = repo.revbranchcache()
    cl = repo.unfiltered().changelog
    branchesdata = collections.defaultdict(lambda: (set(), set()))
    for node in outgoing.missing:
        branch, close = cache.branchinfo(cl.rev(node))
        branchesdata[branch][close].add(node)

    def generate():
        for branch, (nodes, closed) in sorted(branchesdata.items()):
            utf8branch = encoding.fromlocal(branch)
            yield rbcstruct.pack(len(utf8branch), len(nodes), len(closed))
            yield utf8branch
            for n in sorted(nodes):
                yield n
            for n in sorted(closed):
                yield n

    bundler.newpart(b'cache:rev-branch-cache', data=generate(), mandatory=False)


def _formatrequirementsspec(requirements):
    requirements = [req for req in requirements if req != b"shared"]
    return urlreq.quote(b','.join(sorted(requirements)))


def _formatrequirementsparams(requirements):
    requirements = _formatrequirementsspec(requirements)
    params = b"%s%s" % (urlreq.quote(b"requirements="), requirements)
    return params


def format_remote_wanted_sidedata(repo):
    """Formats a repo's wanted sidedata categories into a bytestring for
    capabilities exchange."""
    wanted = b""
    if repo._wanted_sidedata:
        wanted = b','.join(
            pycompat.bytestr(c) for c in sorted(repo._wanted_sidedata)
        )
    return wanted


def read_remote_wanted_sidedata(remote):
    sidedata_categories = remote.capable(b'exp-wanted-sidedata')
    return read_wanted_sidedata(sidedata_categories)


def read_wanted_sidedata(formatted):
    if formatted:
        return set(formatted.split(b','))
    return set()


def addpartbundlestream2(bundler, repo, **kwargs):
    if not kwargs.get('stream', False):
        return

    if not streamclone.allowservergeneration(repo):
        raise error.Abort(
            _(
                b'stream data requested but server does not allow '
                b'this feature'
            ),
            hint=_(
                b'well-behaved clients should not be '
                b'requesting stream data from servers not '
                b'advertising it; the client may be buggy'
            ),
        )

    # Stream clones don't compress well. And compression undermines a
    # goal of stream clones, which is to be fast. Communicate the desire
    # to avoid compression to consumers of the bundle.
    bundler.prefercompressed = False

    # get the includes and excludes
    includepats = kwargs.get('includepats')
    excludepats = kwargs.get('excludepats')

    narrowstream = repo.ui.configbool(
        b'experimental', b'server.stream-narrow-clones'
    )

    if (includepats or excludepats) and not narrowstream:
        raise error.Abort(_(b'server does not support narrow stream clones'))

    includeobsmarkers = False
    if repo.obsstore:
        remoteversions = obsmarkersversion(bundler.capabilities)
        if not remoteversions:
            raise error.Abort(
                _(
                    b'server has obsolescence markers, but client '
                    b'cannot receive them via stream clone'
                )
            )
        elif repo.obsstore._version in remoteversions:
            includeobsmarkers = True

    filecount, bytecount, it = streamclone.generatev2(
        repo, includepats, excludepats, includeobsmarkers
    )
    requirements = _formatrequirementsspec(repo.requirements)
    part = bundler.newpart(b'stream2', data=it)
    part.addparam(b'bytecount', b'%d' % bytecount, mandatory=True)
    part.addparam(b'filecount', b'%d' % filecount, mandatory=True)
    part.addparam(b'requirements', requirements, mandatory=True)


def buildobsmarkerspart(bundler, markers, mandatory=True):
    """add an obsmarker part to the bundler with <markers>

    No part is created if markers is empty.
    Raises ValueError if the bundler doesn't support any known obsmarker format.
    """
    if not markers:
        return None

    remoteversions = obsmarkersversion(bundler.capabilities)
    version = obsolete.commonversion(remoteversions)
    if version is None:
        raise ValueError(b'bundler does not support common obsmarker format')
    stream = obsolete.encodemarkers(markers, True, version=version)
    return bundler.newpart(b'obsmarkers', data=stream, mandatory=mandatory)


def writebundle(
    ui, cg, filename, bundletype, vfs=None, compression=None, compopts=None
):
    """Write a bundle file and return its filename.

    Existing files will not be overwritten.
    If no filename is specified, a temporary file is created.
    bz2 compression can be turned off.
    The bundle file will be deleted in case of errors.
    """

    if bundletype == b"HG20":
        bundle = bundle20(ui)
        bundle.setcompression(compression, compopts)
        part = bundle.newpart(b'changegroup', data=cg.getchunks())
        part.addparam(b'version', cg.version)
        if b'clcount' in cg.extras:
            part.addparam(
                b'nbchanges', b'%d' % cg.extras[b'clcount'], mandatory=False
            )
        chunkiter = bundle.getchunks()
    else:
        # compression argument is only for the bundle2 case
        assert compression is None
        if cg.version != b'01':
            raise error.Abort(
                _(b'old bundle types only supports v1 changegroups')
            )
        header, comp = bundletypes[bundletype]
        if comp not in util.compengines.supportedbundletypes:
            raise error.Abort(_(b'unknown stream compression type: %s') % comp)
        compengine = util.compengines.forbundletype(comp)

        def chunkiter():
            yield header
            for chunk in compengine.compressstream(cg.getchunks(), compopts):
                yield chunk

        chunkiter = chunkiter()

    # parse the changegroup data, otherwise we will block
    # in case of sshrepo because we don't know the end of the stream
    return changegroup.writechunks(ui, chunkiter, filename, vfs=vfs)


def combinechangegroupresults(op):
    """logic to combine 0 or more addchangegroup results into one"""
    results = [r.get(b'return', 0) for r in op.records[b'changegroup']]
    changedheads = 0
    result = 1
    for ret in results:
        # If any changegroup result is 0, return 0
        if ret == 0:
            result = 0
            break
        if ret < -1:
            changedheads += ret + 1
        elif ret > 1:
            changedheads += ret - 1
    if changedheads > 0:
        result = 1 + changedheads
    elif changedheads < 0:
        result = -1 + changedheads
    return result


@parthandler(
    b'changegroup',
    (
        b'version',
        b'nbchanges',
        b'exp-sidedata',
        b'exp-wanted-sidedata',
        b'treemanifest',
        b'targetphase',
    ),
)
def handlechangegroup(op, inpart):
    """apply a changegroup part on the repo"""
    from . import localrepo

    tr = op.gettransaction()
    unpackerversion = inpart.params.get(b'version', b'01')
    # We should raise an appropriate exception here
    cg = changegroup.getunbundler(unpackerversion, inpart, None)
    # the source and url passed here are overwritten by the one contained in
    # the transaction.hookargs argument. So 'bundle2' is a placeholder
    nbchangesets = None
    if b'nbchanges' in inpart.params:
        nbchangesets = int(inpart.params.get(b'nbchanges'))
    if b'treemanifest' in inpart.params and not scmutil.istreemanifest(op.repo):
        if len(op.repo.changelog) != 0:
            raise error.Abort(
                _(
                    b"bundle contains tree manifests, but local repo is "
                    b"non-empty and does not use tree manifests"
                )
            )
        op.repo.requirements.add(requirements.TREEMANIFEST_REQUIREMENT)
        op.repo.svfs.options = localrepo.resolvestorevfsoptions(
            op.repo.ui, op.repo.requirements, op.repo.features
        )
        scmutil.writereporequirements(op.repo)

    extrakwargs = {}
    targetphase = inpart.params.get(b'targetphase')
    if targetphase is not None:
        extrakwargs['targetphase'] = int(targetphase)

    remote_sidedata = inpart.params.get(b'exp-wanted-sidedata')
    extrakwargs['sidedata_categories'] = read_wanted_sidedata(remote_sidedata)

    ret = _processchangegroup(
        op,
        cg,
        tr,
        op.source,
        b'bundle2',
        expectedtotal=nbchangesets,
        **extrakwargs
    )
    if op.reply is not None:
        # This is definitely not the final form of this
        # return. But one need to start somewhere.
        part = op.reply.newpart(b'reply:changegroup', mandatory=False)
        part.addparam(
            b'in-reply-to', pycompat.bytestr(inpart.id), mandatory=False
        )
        part.addparam(b'return', b'%i' % ret, mandatory=False)
    assert not inpart.read()


_remotechangegroupparams = tuple(
    [b'url', b'size', b'digests']
    + [b'digest:%s' % k for k in util.DIGESTS.keys()]
)


@parthandler(b'remote-changegroup', _remotechangegroupparams)
def handleremotechangegroup(op, inpart):
    """apply a bundle10 on the repo, given an url and validation information

    All the information about the remote bundle to import are given as
    parameters. The parameters include:
      - url: the url to the bundle10.
      - size: the bundle10 file size. It is used to validate what was
        retrieved by the client matches the server knowledge about the bundle.
      - digests: a space separated list of the digest types provided as
        parameters.
      - digest:<digest-type>: the hexadecimal representation of the digest with
        that name. Like the size, it is used to validate what was retrieved by
        the client matches what the server knows about the bundle.

    When multiple digest types are given, all of them are checked.
    """
    try:
        raw_url = inpart.params[b'url']
    except KeyError:
        raise error.Abort(_(b'remote-changegroup: missing "%s" param') % b'url')
    parsed_url = urlutil.url(raw_url)
    if parsed_url.scheme not in capabilities[b'remote-changegroup']:
        raise error.Abort(
            _(b'remote-changegroup does not support %s urls')
            % parsed_url.scheme
        )

    try:
        size = int(inpart.params[b'size'])
    except ValueError:
        raise error.Abort(
            _(b'remote-changegroup: invalid value for param "%s"') % b'size'
        )
    except KeyError:
        raise error.Abort(
            _(b'remote-changegroup: missing "%s" param') % b'size'
        )

    digests = {}
    for typ in inpart.params.get(b'digests', b'').split():
        param = b'digest:%s' % typ
        try:
            value = inpart.params[param]
        except KeyError:
            raise error.Abort(
                _(b'remote-changegroup: missing "%s" param') % param
            )
        digests[typ] = value

    real_part = util.digestchecker(url.open(op.ui, raw_url), size, digests)

    tr = op.gettransaction()
    from . import exchange

    cg = exchange.readbundle(op.repo.ui, real_part, raw_url)
    if not isinstance(cg, changegroup.cg1unpacker):
        raise error.Abort(
            _(b'%s: not a bundle version 1.0') % urlutil.hidepassword(raw_url)
        )
    ret = _processchangegroup(op, cg, tr, op.source, b'bundle2')
    if op.reply is not None:
        # This is definitely not the final form of this
        # return. But one need to start somewhere.
        part = op.reply.newpart(b'reply:changegroup')
        part.addparam(
            b'in-reply-to', pycompat.bytestr(inpart.id), mandatory=False
        )
        part.addparam(b'return', b'%i' % ret, mandatory=False)
    try:
        real_part.validate()
    except error.Abort as e:
        raise error.Abort(
            _(b'bundle at %s is corrupted:\n%s')
            % (urlutil.hidepassword(raw_url), e.message)
        )
    assert not inpart.read()


@parthandler(b'reply:changegroup', (b'return', b'in-reply-to'))
def handlereplychangegroup(op, inpart):
    ret = int(inpart.params[b'return'])
    replyto = int(inpart.params[b'in-reply-to'])
    op.records.add(b'changegroup', {b'return': ret}, replyto)


@parthandler(b'check:bookmarks')
def handlecheckbookmarks(op, inpart):
    """check location of bookmarks

    This part is to be used to detect push race regarding bookmark, it
    contains binary encoded (bookmark, node) tuple. If the local state does
    not marks the one in the part, a PushRaced exception is raised
    """
    bookdata = bookmarks.binarydecode(op.repo, inpart)

    msgstandard = (
        b'remote repository changed while pushing - please try again '
        b'(bookmark "%s" move from %s to %s)'
    )
    msgmissing = (
        b'remote repository changed while pushing - please try again '
        b'(bookmark "%s" is missing, expected %s)'
    )
    msgexist = (
        b'remote repository changed while pushing - please try again '
        b'(bookmark "%s" set on %s, expected missing)'
    )
    for book, node in bookdata:
        currentnode = op.repo._bookmarks.get(book)
        if currentnode != node:
            if node is None:
                finalmsg = msgexist % (book, short(currentnode))
            elif currentnode is None:
                finalmsg = msgmissing % (book, short(node))
            else:
                finalmsg = msgstandard % (
                    book,
                    short(node),
                    short(currentnode),
                )
            raise error.PushRaced(finalmsg)


@parthandler(b'check:heads')
def handlecheckheads(op, inpart):
    """check that head of the repo did not change

    This is used to detect a push race when using unbundle.
    This replaces the "heads" argument of unbundle."""
    h = inpart.read(20)
    heads = []
    while len(h) == 20:
        heads.append(h)
        h = inpart.read(20)
    assert not h
    # Trigger a transaction so that we are guaranteed to have the lock now.
    if op.ui.configbool(b'experimental', b'bundle2lazylocking'):
        op.gettransaction()
    if sorted(heads) != sorted(op.repo.heads()):
        raise error.PushRaced(
            b'remote repository changed while pushing - please try again'
        )


@parthandler(b'check:updated-heads')
def handlecheckupdatedheads(op, inpart):
    """check for race on the heads touched by a push

    This is similar to 'check:heads' but focus on the heads actually updated
    during the push. If other activities happen on unrelated heads, it is
    ignored.

    This allow server with high traffic to avoid push contention as long as
    unrelated parts of the graph are involved."""
    h = inpart.read(20)
    heads = []
    while len(h) == 20:
        heads.append(h)
        h = inpart.read(20)
    assert not h
    # trigger a transaction so that we are guaranteed to have the lock now.
    if op.ui.configbool(b'experimental', b'bundle2lazylocking'):
        op.gettransaction()

    currentheads = set()
    for ls in op.repo.branchmap().iterheads():
        currentheads.update(ls)

    for h in heads:
        if h not in currentheads:
            raise error.PushRaced(
                b'remote repository changed while pushing - '
                b'please try again'
            )


@parthandler(b'check:phases')
def handlecheckphases(op, inpart):
    """check that phase boundaries of the repository did not change

    This is used to detect a push race.
    """
    phasetonodes = phases.binarydecode(inpart)
    unfi = op.repo.unfiltered()
    cl = unfi.changelog
    phasecache = unfi._phasecache
    msg = (
        b'remote repository changed while pushing - please try again '
        b'(%s is %s expected %s)'
    )
    for expectedphase, nodes in pycompat.iteritems(phasetonodes):
        for n in nodes:
            actualphase = phasecache.phase(unfi, cl.rev(n))
            if actualphase != expectedphase:
                finalmsg = msg % (
                    short(n),
                    phases.phasenames[actualphase],
                    phases.phasenames[expectedphase],
                )
                raise error.PushRaced(finalmsg)


@parthandler(b'output')
def handleoutput(op, inpart):
    """forward output captured on the server to the client"""
    for line in inpart.read().splitlines():
        op.ui.status(_(b'remote: %s\n') % line)


@parthandler(b'replycaps')
def handlereplycaps(op, inpart):
    """Notify that a reply bundle should be created

    The payload contains the capabilities information for the reply"""
    caps = decodecaps(inpart.read())
    if op.reply is None:
        op.reply = bundle20(op.ui, caps)


class AbortFromPart(error.Abort):
    """Sub-class of Abort that denotes an error from a bundle2 part."""


@parthandler(b'error:abort', (b'message', b'hint'))
def handleerrorabort(op, inpart):
    """Used to transmit abort error over the wire"""
    raise AbortFromPart(
        inpart.params[b'message'], hint=inpart.params.get(b'hint')
    )


@parthandler(
    b'error:pushkey',
    (b'namespace', b'key', b'new', b'old', b'ret', b'in-reply-to'),
)
def handleerrorpushkey(op, inpart):
    """Used to transmit failure of a mandatory pushkey over the wire"""
    kwargs = {}
    for name in (b'namespace', b'key', b'new', b'old', b'ret'):
        value = inpart.params.get(name)
        if value is not None:
            kwargs[name] = value
    raise error.PushkeyFailed(
        inpart.params[b'in-reply-to'], **pycompat.strkwargs(kwargs)
    )


@parthandler(b'error:unsupportedcontent', (b'parttype', b'params'))
def handleerrorunsupportedcontent(op, inpart):
    """Used to transmit unknown content error over the wire"""
    kwargs = {}
    parttype = inpart.params.get(b'parttype')
    if parttype is not None:
        kwargs[b'parttype'] = parttype
    params = inpart.params.get(b'params')
    if params is not None:
        kwargs[b'params'] = params.split(b'\0')

    raise error.BundleUnknownFeatureError(**pycompat.strkwargs(kwargs))


@parthandler(b'error:pushraced', (b'message',))
def handleerrorpushraced(op, inpart):
    """Used to transmit push race error over the wire"""
    raise error.ResponseError(_(b'push failed:'), inpart.params[b'message'])


@parthandler(b'listkeys', (b'namespace',))
def handlelistkeys(op, inpart):
    """retrieve pushkey namespace content stored in a bundle2"""
    namespace = inpart.params[b'namespace']
    r = pushkey.decodekeys(inpart.read())
    op.records.add(b'listkeys', (namespace, r))


@parthandler(b'pushkey', (b'namespace', b'key', b'old', b'new'))
def handlepushkey(op, inpart):
    """process a pushkey request"""
    dec = pushkey.decode
    namespace = dec(inpart.params[b'namespace'])
    key = dec(inpart.params[b'key'])
    old = dec(inpart.params[b'old'])
    new = dec(inpart.params[b'new'])
    # Grab the transaction to ensure that we have the lock before performing the
    # pushkey.
    if op.ui.configbool(b'experimental', b'bundle2lazylocking'):
        op.gettransaction()
    ret = op.repo.pushkey(namespace, key, old, new)
    record = {b'namespace': namespace, b'key': key, b'old': old, b'new': new}
    op.records.add(b'pushkey', record)
    if op.reply is not None:
        rpart = op.reply.newpart(b'reply:pushkey')
        rpart.addparam(
            b'in-reply-to', pycompat.bytestr(inpart.id), mandatory=False
        )
        rpart.addparam(b'return', b'%i' % ret, mandatory=False)
    if inpart.mandatory and not ret:
        kwargs = {}
        for key in (b'namespace', b'key', b'new', b'old', b'ret'):
            if key in inpart.params:
                kwargs[key] = inpart.params[key]
        raise error.PushkeyFailed(
            partid=b'%d' % inpart.id, **pycompat.strkwargs(kwargs)
        )


@parthandler(b'bookmarks')
def handlebookmark(op, inpart):
    """transmit bookmark information

    The part contains binary encoded bookmark information.

    The exact behavior of this part can be controlled by the 'bookmarks' mode
    on the bundle operation.

    When mode is 'apply' (the default) the bookmark information is applied as
    is to the unbundling repository. Make sure a 'check:bookmarks' part is
    issued earlier to check for push races in such update. This behavior is
    suitable for pushing.

    When mode is 'records', the information is recorded into the 'bookmarks'
    records of the bundle operation. This behavior is suitable for pulling.
    """
    changes = bookmarks.binarydecode(op.repo, inpart)

    pushkeycompat = op.repo.ui.configbool(
        b'server', b'bookmarks-pushkey-compat'
    )
    bookmarksmode = op.modes.get(b'bookmarks', b'apply')

    if bookmarksmode == b'apply':
        tr = op.gettransaction()
        bookstore = op.repo._bookmarks
        if pushkeycompat:
            allhooks = []
            for book, node in changes:
                hookargs = tr.hookargs.copy()
                hookargs[b'pushkeycompat'] = b'1'
                hookargs[b'namespace'] = b'bookmarks'
                hookargs[b'key'] = book
                hookargs[b'old'] = hex(bookstore.get(book, b''))
                hookargs[b'new'] = hex(node if node is not None else b'')
                allhooks.append(hookargs)

            for hookargs in allhooks:
                op.repo.hook(
                    b'prepushkey', throw=True, **pycompat.strkwargs(hookargs)
                )

        for book, node in changes:
            if bookmarks.isdivergent(book):
                msg = _(b'cannot accept divergent bookmark %s!') % book
                raise error.Abort(msg)

        bookstore.applychanges(op.repo, op.gettransaction(), changes)

        if pushkeycompat:

            def runhook(unused_success):
                for hookargs in allhooks:
                    op.repo.hook(b'pushkey', **pycompat.strkwargs(hookargs))

            op.repo._afterlock(runhook)

    elif bookmarksmode == b'records':
        for book, node in changes:
            record = {b'bookmark': book, b'node': node}
            op.records.add(b'bookmarks', record)
    else:
        raise error.ProgrammingError(
            b'unkown bookmark mode: %s' % bookmarksmode
        )


@parthandler(b'phase-heads')
def handlephases(op, inpart):
    """apply phases from bundle part to repo"""
    headsbyphase = phases.binarydecode(inpart)
    phases.updatephases(op.repo.unfiltered(), op.gettransaction, headsbyphase)


@parthandler(b'reply:pushkey', (b'return', b'in-reply-to'))
def handlepushkeyreply(op, inpart):
    """retrieve the result of a pushkey request"""
    ret = int(inpart.params[b'return'])
    partid = int(inpart.params[b'in-reply-to'])
    op.records.add(b'pushkey', {b'return': ret}, partid)


@parthandler(b'obsmarkers')
def handleobsmarker(op, inpart):
    """add a stream of obsmarkers to the repo"""
    tr = op.gettransaction()
    markerdata = inpart.read()
    if op.ui.config(b'experimental', b'obsmarkers-exchange-debug'):
        op.ui.writenoi18n(
            b'obsmarker-exchange: %i bytes received\n' % len(markerdata)
        )
    # The mergemarkers call will crash if marker creation is not enabled.
    # we want to avoid this if the part is advisory.
    if not inpart.mandatory and op.repo.obsstore.readonly:
        op.repo.ui.debug(
            b'ignoring obsolescence markers, feature not enabled\n'
        )
        return
    new = op.repo.obsstore.mergemarkers(tr, markerdata)
    op.repo.invalidatevolatilesets()
    op.records.add(b'obsmarkers', {b'new': new})
    if op.reply is not None:
        rpart = op.reply.newpart(b'reply:obsmarkers')
        rpart.addparam(
            b'in-reply-to', pycompat.bytestr(inpart.id), mandatory=False
        )
        rpart.addparam(b'new', b'%i' % new, mandatory=False)


@parthandler(b'reply:obsmarkers', (b'new', b'in-reply-to'))
def handleobsmarkerreply(op, inpart):
    """retrieve the result of a pushkey request"""
    ret = int(inpart.params[b'new'])
    partid = int(inpart.params[b'in-reply-to'])
    op.records.add(b'obsmarkers', {b'new': ret}, partid)


@parthandler(b'hgtagsfnodes')
def handlehgtagsfnodes(op, inpart):
    """Applies .hgtags fnodes cache entries to the local repo.

    Payload is pairs of 20 byte changeset nodes and filenodes.
    """
    # Grab the transaction so we ensure that we have the lock at this point.
    if op.ui.configbool(b'experimental', b'bundle2lazylocking'):
        op.gettransaction()
    cache = tags.hgtagsfnodescache(op.repo.unfiltered())

    count = 0
    while True:
        node = inpart.read(20)
        fnode = inpart.read(20)
        if len(node) < 20 or len(fnode) < 20:
            op.ui.debug(b'ignoring incomplete received .hgtags fnodes data\n')
            break
        cache.setfnode(node, fnode)
        count += 1

    cache.write()
    op.ui.debug(b'applied %i hgtags fnodes cache entries\n' % count)


rbcstruct = struct.Struct(b'>III')


@parthandler(b'cache:rev-branch-cache')
def handlerbc(op, inpart):
    """Legacy part, ignored for compatibility with bundles from or
    for Mercurial before 5.7. Newer Mercurial computes the cache
    efficiently enough during unbundling that the additional transfer
    is unnecessary."""


@parthandler(b'pushvars')
def bundle2getvars(op, part):
    '''unbundle a bundle2 containing shellvars on the server'''
    # An option to disable unbundling on server-side for security reasons
    if op.ui.configbool(b'push', b'pushvars.server'):
        hookargs = {}
        for key, value in part.advisoryparams:
            key = key.upper()
            # We want pushed variables to have USERVAR_ prepended so we know
            # they came from the --pushvar flag.
            key = b"USERVAR_" + key
            hookargs[key] = value
        op.addhookargs(hookargs)


@parthandler(b'stream2', (b'requirements', b'filecount', b'bytecount'))
def handlestreamv2bundle(op, part):

    requirements = urlreq.unquote(part.params[b'requirements']).split(b',')
    filecount = int(part.params[b'filecount'])
    bytecount = int(part.params[b'bytecount'])

    repo = op.repo
    if len(repo):
        msg = _(b'cannot apply stream clone to non empty repository')
        raise error.Abort(msg)

    repo.ui.debug(b'applying stream bundle\n')
    streamclone.applybundlev2(repo, part, filecount, bytecount, requirements)


def widen_bundle(
    bundler, repo, oldmatcher, newmatcher, common, known, cgversion, ellipses
):
    """generates bundle2 for widening a narrow clone

    bundler is the bundle to which data should be added
    repo is the localrepository instance
    oldmatcher matches what the client already has
    newmatcher matches what the client needs (including what it already has)
    common is set of common heads between server and client
    known is a set of revs known on the client side (used in ellipses)
    cgversion is the changegroup version to send
    ellipses is boolean value telling whether to send ellipses data or not

    returns bundle2 of the data required for extending
    """
    commonnodes = set()
    cl = repo.changelog
    for r in repo.revs(b"::%ln", common):
        commonnodes.add(cl.node(r))
    if commonnodes:
        packer = changegroup.getbundler(
            cgversion,
            repo,
            oldmatcher=oldmatcher,
            matcher=newmatcher,
            fullnodes=commonnodes,
        )
        cgdata = packer.generate(
            {repo.nullid},
            list(commonnodes),
            False,
            b'narrow_widen',
            changelog=False,
        )

        part = bundler.newpart(b'changegroup', data=cgdata)
        part.addparam(b'version', cgversion)
        if scmutil.istreemanifest(repo):
            part.addparam(b'treemanifest', b'1')
    if repository.REPO_FEATURE_SIDE_DATA in repo.features:
        part.addparam(b'exp-sidedata', b'1')
        wanted = format_remote_wanted_sidedata(repo)
        part.addparam(b'exp-wanted-sidedata', wanted)

    return bundler
