# Copyright 2009-2010 Gregory P. Ward
# Copyright 2009-2010 Intelerad Medical Systems Incorporated
# Copyright 2010-2011 Fog Creek Software
# Copyright 2010-2011 Unity Technologies
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''base class for store implementations and store-related utility code'''

import re

from mercurial import util, node, hg
from mercurial.i18n import _

import lfutil

class StoreError(Exception):
    '''Raised when there is a problem getting files from or putting
    files to a central store.'''
    def __init__(self, filename, hash, url, detail):
        self.filename = filename
        self.hash = hash
        self.url = url
        self.detail = detail

    def longmessage(self):
        return (_("error getting id %s from url %s for file %s: %s\n") %
                 (self.hash, self.url, self.filename, self.detail))

    def __str__(self):
        return "%s: %s" % (self.url, self.detail)

class basestore(object):
    def __init__(self, ui, repo, url):
        self.ui = ui
        self.repo = repo
        self.url = url

    def put(self, source, hash):
        '''Put source file into the store so it can be retrieved by hash.'''
        raise NotImplementedError('abstract method')

    def exists(self, hashes):
        '''Check to see if the store contains the given hashes. Given an
        iterable of hashes it returns a mapping from hash to bool.'''
        raise NotImplementedError('abstract method')

    def get(self, files):
        '''Get the specified largefiles from the store and write to local
        files under repo.root.  files is a list of (filename, hash)
        tuples.  Return (success, missing), lists of files successfully
        downloaded and those not found in the store.  success is a list
        of (filename, hash) tuples; missing is a list of filenames that
        we could not get.  (The detailed error message will already have
        been presented to the user, so missing is just supplied as a
        summary.)'''
        success = []
        missing = []
        ui = self.ui

        util.makedirs(lfutil.storepath(self.repo, ''))

        at = 0
        available = self.exists(set(hash for (_filename, hash) in files))
        for filename, hash in files:
            ui.progress(_('getting largefiles'), at, unit='lfile',
                total=len(files))
            at += 1
            ui.note(_('getting %s:%s\n') % (filename, hash))

            if not available.get(hash):
                ui.warn(_('%s: largefile %s not available from %s\n')
                        % (filename, hash, self.url))
                missing.append(filename)
                continue

            storefilename = lfutil.storepath(self.repo, hash)
            tmpfile = util.atomictempfile(storefilename + '.tmp',
                                          createmode=self.repo.store.createmode)

            try:
                hhash = self._getfile(tmpfile, filename, hash)
            except StoreError, err:
                ui.warn(err.longmessage())
                hhash = ""
            tmpfile.close()

            if hhash != hash:
                if hhash != "":
                    ui.warn(_('%s: data corruption (expected %s, got %s)\n')
                            % (filename, hash, hhash))
                util.unlink(storefilename + '.tmp')
                missing.append(filename)
                continue

            util.rename(storefilename + '.tmp', storefilename)
            lfutil.linktousercache(self.repo, hash)
            success.append((filename, hhash))

        ui.progress(_('getting largefiles'), None)
        return (success, missing)

    def verify(self, revs, contents=False):
        '''Verify the existence (and, optionally, contents) of every big
        file revision referenced by every changeset in revs.
        Return 0 if all is well, non-zero on any errors.'''
        failed = False

        self.ui.status(_('searching %d changesets for largefiles\n') %
                       len(revs))
        verified = set()                # set of (filename, filenode) tuples

        for rev in revs:
            cctx = self.repo[rev]
            cset = "%d:%s" % (cctx.rev(), node.short(cctx.node()))

            for standin in cctx:
                if self._verifyfile(cctx, cset, contents, standin, verified):
                    failed = True

        numrevs = len(verified)
        numlfiles = len(set([fname for (fname, fnode) in verified]))
        if contents:
            self.ui.status(
                _('verified contents of %d revisions of %d largefiles\n')
                % (numrevs, numlfiles))
        else:
            self.ui.status(
                _('verified existence of %d revisions of %d largefiles\n')
                % (numrevs, numlfiles))
        return int(failed)

    def _getfile(self, tmpfile, filename, hash):
        '''Fetch one revision of one file from the store and write it
        to tmpfile.  Compute the hash of the file on-the-fly as it
        downloads and return the hash.  Close tmpfile.  Raise
        StoreError if unable to download the file (e.g. it does not
        exist in the store).'''
        raise NotImplementedError('abstract method')

    def _verifyfile(self, cctx, cset, contents, standin, verified):
        '''Perform the actual verification of a file in the store.
        'cset' is only used in warnings.
        'contents' controls verification of content hash.
        'standin' is the standin path of the largefile to verify.
        'verified' is maintained as a set of already verified files.
        Returns _true_ if it is a standin and any problems are found!
        '''
        raise NotImplementedError('abstract method')

import localstore, wirestore

_storeprovider = {
    'file':  [localstore.localstore],
    'http':  [wirestore.wirestore],
    'https': [wirestore.wirestore],
    'ssh': [wirestore.wirestore],
    }

_scheme_re = re.compile(r'^([a-zA-Z0-9+-.]+)://')

# During clone this function is passed the src's ui object
# but it needs the dest's ui object so it can read out of
# the config file. Use repo.ui instead.
def _openstore(repo, remote=None, put=False):
    ui = repo.ui

    if not remote:
        lfpullsource = getattr(repo, 'lfpullsource', None)
        if lfpullsource:
            path = ui.expandpath(lfpullsource)
        else:
            path = ui.expandpath('default-push', 'default')

        # ui.expandpath() leaves 'default-push' and 'default' alone if
        # they cannot be expanded: fallback to the empty string,
        # meaning the current directory.
        if path == 'default-push' or path == 'default':
            path = ''
            remote = repo
        else:
            path, _branches = hg.parseurl(path)
            remote = hg.peer(repo, {}, path)

    # The path could be a scheme so use Mercurial's normal functionality
    # to resolve the scheme to a repository and use its path
    path = util.safehasattr(remote, 'url') and remote.url() or remote.path

    match = _scheme_re.match(path)
    if not match:                       # regular filesystem path
        scheme = 'file'
    else:
        scheme = match.group(1)

    try:
        storeproviders = _storeprovider[scheme]
    except KeyError:
        raise util.Abort(_('unsupported URL scheme %r') % scheme)

    for classobj in storeproviders:
        try:
            return classobj(ui, repo, remote)
        except lfutil.storeprotonotcapable:
            pass

    raise util.Abort(_('%s does not appear to be a largefile store') % path)
