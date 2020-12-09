#!/usr/bin/env $PYTHON$
#  Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import os
import socket
import struct
import sys
import traceback

# See com.intellij.idea.SocketLock for the server side of this interface.

RUN_PATH = u'$RUN_PATH$'
CONFIG_PATH = u'$CONFIG_PATH$'
SYSTEM_PATH = u'$SYSTEM_PATH$'


def print_usage(cmd):
    print(('Usage:\n' +
           '  {0} -h | -? | --help\n' +
           '  {0} [project_dir] [-w|--wait]\n' +
           '  {0} [-l|--line line] [project_dir|--temp-project] [-w|--wait] file[:line]\n' +
           '  {0} diff <left> <right>\n' +
           '  {0} merge <local> <remote> [base] <merged>').format(cmd))


def write_to_sock(sock, data):
    if sys.version_info[0] >= 3:
        data = data.encode('utf-8')
    sock.send(struct.pack('>h', len(data)) + data)


def read_from_sock(sock):
    length = struct.unpack('>h', sock.recv(2))[0]
    return sock.recv(length).decode('utf-8')


def read_sequence_from_sock(sock):
    result = []
    while True:
        try:
            data = read_from_sock(sock)
            if data == '---':
                break
            result.append(data)
        except (socket.error, IOError) as e:
            print("I/O error({0}): {1} ({2})".format(e.errno, e.strerror, e))
            traceback.print_exception(*sys.exc_info())
            break
    return result


def process_args(argv):
    args = []

    skip_next = False
    for i, arg in enumerate(argv[1:]):
        if arg == '-h' or arg == '-?' or arg == '--help':
            print_usage(argv[0])
            exit(0)
        elif i == 0 and (arg == 'diff' or arg == 'merge' or arg == '--temp-project'):
            args.append(arg)
        elif arg == '-l' or arg == '--line':
            args.append(arg)
            skip_next = True
        elif arg == '-w' or arg == '--wait':
            args.append('--wait')
        elif arg == '-p' or arg == '--project':
            args.append(arg)
        elif arg == '-e' or arg == '--edit':
            args.append(arg)
        elif skip_next:
            args.append(arg)
            skip_next = False
        else:
            path = arg
            if ':' in arg:
                file_path, line_number = arg.rsplit(':', 1)
                if line_number.isdigit():
                    args.append('-l')
                    args.append(line_number)
                    path = file_path
            args.append(os.path.abspath(path))

    return args


def try_activate_instance(args):
    port_path = os.path.join(CONFIG_PATH, 'port')
    token_path = os.path.join(SYSTEM_PATH, 'token')
    if not (os.path.exists(port_path) and os.path.exists(token_path)):
        return False

    try:
        with open(port_path) as pf:
            port = int(pf.read())
        with open(token_path) as tf:
            token = tf.read()
    except ValueError:
        return False

    s = socket.socket()
    s.settimeout(1.0)
    try:
        s.connect(('127.0.0.1', port))
    except (socket.error, IOError):
        return False

    paths = read_sequence_from_sock(s)
    found = CONFIG_PATH in paths or os.path.realpath(CONFIG_PATH) in paths

    if found:
        write_to_sock(s, 'activate ' + token + '\0' + os.getcwd() + '\0' + '\0'.join(args))

        s.settimeout(None)
        response = read_sequence_from_sock(s)
        if len(response) < 2 or response[0] != 'ok':
            print('bad response: ' + str(response))
            exit(1)

        if len(response) > 2:
            print(response[2])

        exit(int(response[1]))

    return False


def start_new_instance(args):
    if sys.platform == 'darwin':
        if len(args) > 0:
            args.insert(0, '--args')
        if '--wait' in args:
            args.insert(0, '-W')
        os.execv('/usr/bin/open', ['open', '-na', RUN_PATH] + args)
    else:
        bin_file = os.path.split(RUN_PATH)[1]
        os.execv(RUN_PATH, [bin_file] + args)


ide_args = process_args(sys.argv)
if not try_activate_instance(ide_args):
    start_new_instance(ide_args)
