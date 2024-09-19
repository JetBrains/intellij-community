# sshserver.py - ssh protocol server support for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import util, hook, wireproto, changegroup
import os, sys

class sshserver(object):
    def __init__(self, ui, repo):
        self.ui = ui
        self.repo = repo
        self.lock = None
        self.fin = ui.fin
        self.fout = ui.fout

        hook.redirect(True)
        ui.fout = repo.ui.fout = ui.ferr

        # Prevent insertion/deletion of CRs
        util.setbinary(self.fin)
        util.setbinary(self.fout)

    def getargs(self, args):
        data = {}
        keys = args.split()
        for n in xrange(len(keys)):
            argline = self.fin.readline()[:-1]
            arg, l = argline.split()
            if arg not in keys:
                raise util.Abort("unexpected parameter %r" % arg)
            if arg == '*':
                star = {}
                for k in xrange(int(l)):
                    argline = self.fin.readline()[:-1]
                    arg, l = argline.split()
                    val = self.fin.read(int(l))
                    star[arg] = val
                data['*'] = star
            else:
                val = self.fin.read(int(l))
                data[arg] = val
        return [data[k] for k in keys]

    def getarg(self, name):
        return self.getargs(name)[0]

    def getfile(self, fpout):
        self.sendresponse('')
        count = int(self.fin.readline())
        while count:
            fpout.write(self.fin.read(count))
            count = int(self.fin.readline())

    def redirect(self):
        pass

    def groupchunks(self, changegroup):
        while True:
            d = changegroup.read(4096)
            if not d:
                break
            yield d

    def sendresponse(self, v):
        self.fout.write("%d\n" % len(v))
        self.fout.write(v)
        self.fout.flush()

    def sendstream(self, source):
        write = self.fout.write
        for chunk in source.gen:
            write(chunk)
        self.fout.flush()

    def sendpushresponse(self, rsp):
        self.sendresponse('')
        self.sendresponse(str(rsp.res))

    def sendpusherror(self, rsp):
        self.sendresponse(rsp.res)

    def sendooberror(self, rsp):
        self.ui.ferr.write('%s\n-\n' % rsp.message)
        self.ui.ferr.flush()
        self.fout.write('\n')
        self.fout.flush()

    def serve_forever(self):
        try:
            while self.serve_one():
                pass
        finally:
            if self.lock is not None:
                self.lock.release()
        sys.exit(0)

    handlers = {
        str: sendresponse,
        wireproto.streamres: sendstream,
        wireproto.pushres: sendpushresponse,
        wireproto.pusherr: sendpusherror,
        wireproto.ooberror: sendooberror,
    }

    def serve_one(self):
        cmd = self.fin.readline()[:-1]
        if cmd and cmd in wireproto.commands:
            rsp = wireproto.dispatch(self.repo, self, cmd)
            self.handlers[rsp.__class__](self, rsp)
        elif cmd:
            impl = getattr(self, 'do_' + cmd, None)
            if impl:
                r = impl()
                if r is not None:
                    self.sendresponse(r)
            else: self.sendresponse("")
        return cmd != ''

    def do_lock(self):
        '''DEPRECATED - allowing remote client to lock repo is not safe'''

        self.lock = self.repo.lock()
        return ""

    def do_unlock(self):
        '''DEPRECATED'''

        if self.lock:
            self.lock.release()
        self.lock = None
        return ""

    def do_addchangegroup(self):
        '''DEPRECATED'''

        if not self.lock:
            self.sendresponse("not locked")
            return

        self.sendresponse("")
        cg = changegroup.unbundle10(self.fin, "UN")
        r = self.repo.addchangegroup(cg, 'serve', self._client())
        self.lock.release()
        return str(r)

    def _client(self):
        client = os.environ.get('SSH_CLIENT', '').split(' ', 1)[0]
        return 'remote:ssh:' + client
