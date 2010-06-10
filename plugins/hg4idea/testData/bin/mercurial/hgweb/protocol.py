#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import cStringIO, zlib, tempfile, errno, os, sys, urllib, copy
from mercurial import util, streamclone
from mercurial.node import bin, hex
from mercurial import changegroup as changegroupmod
from common import ErrorResponse, HTTP_OK, HTTP_NOT_FOUND, HTTP_SERVER_ERROR

# __all__ is populated with the allowed commands. Be sure to add to it if
# you're adding a new command, or the new command won't work.

__all__ = [
   'lookup', 'heads', 'branches', 'between', 'changegroup',
   'changegroupsubset', 'capabilities', 'unbundle', 'stream_out',
   'branchmap',
]

HGTYPE = 'application/mercurial-0.1'
basecaps = 'lookup changegroupsubset branchmap'.split()

def lookup(repo, req):
    try:
        r = hex(repo.lookup(req.form['key'][0]))
        success = 1
    except Exception, inst:
        r = str(inst)
        success = 0
    resp = "%s %s\n" % (success, r)
    req.respond(HTTP_OK, HGTYPE, length=len(resp))
    yield resp

def heads(repo, req):
    resp = " ".join(map(hex, repo.heads())) + "\n"
    req.respond(HTTP_OK, HGTYPE, length=len(resp))
    yield resp

def branchmap(repo, req):
    branches = repo.branchmap()
    heads = []
    for branch, nodes in branches.iteritems():
        branchname = urllib.quote(branch)
        branchnodes = [hex(node) for node in nodes]
        heads.append('%s %s' % (branchname, ' '.join(branchnodes)))
    resp = '\n'.join(heads)
    req.respond(HTTP_OK, HGTYPE, length=len(resp))
    yield resp

def branches(repo, req):
    nodes = []
    if 'nodes' in req.form:
        nodes = map(bin, req.form['nodes'][0].split(" "))
    resp = cStringIO.StringIO()
    for b in repo.branches(nodes):
        resp.write(" ".join(map(hex, b)) + "\n")
    resp = resp.getvalue()
    req.respond(HTTP_OK, HGTYPE, length=len(resp))
    yield resp

def between(repo, req):
    pairs = [map(bin, p.split("-"))
             for p in req.form['pairs'][0].split(" ")]
    resp = ''.join(" ".join(map(hex, b)) + "\n" for b in repo.between(pairs))
    req.respond(HTTP_OK, HGTYPE, length=len(resp))
    yield resp

def changegroup(repo, req):
    req.respond(HTTP_OK, HGTYPE)
    nodes = []

    if 'roots' in req.form:
        nodes = map(bin, req.form['roots'][0].split(" "))

    z = zlib.compressobj()
    f = repo.changegroup(nodes, 'serve')
    while 1:
        chunk = f.read(4096)
        if not chunk:
            break
        yield z.compress(chunk)

    yield z.flush()

def changegroupsubset(repo, req):
    req.respond(HTTP_OK, HGTYPE)
    bases = []
    heads = []

    if 'bases' in req.form:
        bases = [bin(x) for x in req.form['bases'][0].split(' ')]
    if 'heads' in req.form:
        heads = [bin(x) for x in req.form['heads'][0].split(' ')]

    z = zlib.compressobj()
    f = repo.changegroupsubset(bases, heads, 'serve')
    while 1:
        chunk = f.read(4096)
        if not chunk:
            break
        yield z.compress(chunk)

    yield z.flush()

def capabilities(repo, req):
    caps = copy.copy(basecaps)
    if streamclone.allowed(repo.ui):
        caps.append('stream=%d' % repo.changelog.version)
    if changegroupmod.bundlepriority:
        caps.append('unbundle=%s' % ','.join(changegroupmod.bundlepriority))
    rsp = ' '.join(caps)
    req.respond(HTTP_OK, HGTYPE, length=len(rsp))
    yield rsp

def unbundle(repo, req):

    proto = req.env.get('wsgi.url_scheme') or 'http'
    their_heads = req.form['heads'][0].split(' ')

    def check_heads():
        heads = map(hex, repo.heads())
        return their_heads == [hex('force')] or their_heads == heads

    # fail early if possible
    if not check_heads():
        req.drain()
        raise ErrorResponse(HTTP_OK, 'unsynced changes')

    # do not lock repo until all changegroup data is
    # streamed. save to temporary file.

    fd, tempname = tempfile.mkstemp(prefix='hg-unbundle-')
    fp = os.fdopen(fd, 'wb+')
    try:
        length = int(req.env['CONTENT_LENGTH'])
        for s in util.filechunkiter(req, limit=length):
            fp.write(s)

        try:
            lock = repo.lock()
            try:
                if not check_heads():
                    raise ErrorResponse(HTTP_OK, 'unsynced changes')

                fp.seek(0)
                header = fp.read(6)
                if header.startswith('HG') and not header.startswith('HG10'):
                    raise ValueError('unknown bundle version')
                elif header not in changegroupmod.bundletypes:
                    raise ValueError('unknown bundle compression type')
                gen = changegroupmod.unbundle(header, fp)

                # send addchangegroup output to client

                oldio = sys.stdout, sys.stderr
                sys.stderr = sys.stdout = cStringIO.StringIO()

                try:
                    url = 'remote:%s:%s:%s' % (
                          proto,
                          urllib.quote(req.env.get('REMOTE_HOST', '')),
                          urllib.quote(req.env.get('REMOTE_USER', '')))
                    try:
                        ret = repo.addchangegroup(gen, 'serve', url)
                    except util.Abort, inst:
                        sys.stdout.write("abort: %s\n" % inst)
                        ret = 0
                finally:
                    val = sys.stdout.getvalue()
                    sys.stdout, sys.stderr = oldio
                req.respond(HTTP_OK, HGTYPE)
                return '%d\n%s' % (ret, val),
            finally:
                lock.release()
        except ValueError, inst:
            raise ErrorResponse(HTTP_OK, inst)
        except (OSError, IOError), inst:
            error = getattr(inst, 'strerror', 'Unknown error')
            if not isinstance(error, str):
                error = 'Error: %s' % str(error)
            if inst.errno == errno.ENOENT:
                code = HTTP_NOT_FOUND
            else:
                code = HTTP_SERVER_ERROR
            filename = getattr(inst, 'filename', '')
            # Don't send our filesystem layout to the client
            if filename and filename.startswith(repo.root):
                filename = filename[len(repo.root)+1:]
                text = '%s: %s' % (error, filename)
            else:
                text = error.replace(repo.root + os.path.sep, '')
            raise ErrorResponse(code, text)
    finally:
        fp.close()
        os.unlink(tempname)

def stream_out(repo, req):
    req.respond(HTTP_OK, HGTYPE)
    try:
        for chunk in streamclone.stream_out(repo):
            yield chunk
    except streamclone.StreamException, inst:
        yield str(inst)
