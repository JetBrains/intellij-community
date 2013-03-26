#!/usr/bin/python

import socket
import struct
import sys
import os
import time

# see com.intellij.idea.SocketLock for the server side of this interface

RUN_PATH = '$RUN_PATH$'
CONFIG_PATH = '$CONFIG_PATH$'

args = []
skip_next = False
for i, arg in enumerate(sys.argv[1:]):
    if arg == '-h' or arg == '-?' or arg == '--help':
        print(('Usage:\n' + \
               '  {0} -h |-? | --help\n' + \
               '  {0} [-l|--line line] file[:line]\n' + \
               '  {0} diff file1 file2').format(sys.argv[0]))
        exit(0)
    elif arg == 'diff' and i == 0:
        args.append(arg)
    elif arg == '-l' or arg == '--line':
        args.append(arg)
        skip_next = True
    elif skip_next:
        args.append(arg)
        skip_next = False
    else:
        if ':' in arg:
            filepath, line_number = arg.rsplit(':', 1)
            if line_number.isdigit():
              args.append('-l')
              args.append(line_number)
              args.append(os.path.abspath(filepath))
            else:
              args.append(os.path.abspath(arg))
        else:
            args.append(os.path.abspath(arg))

def launch_with_port(port):
    found = False

    s = socket.socket()
    s.settimeout(0.3)
    try:
        s.connect(('127.0.0.1', port))
    except:
        return False
   
    while True:
        try:
            path_len = struct.unpack(">h", s.recv(2))[0]
            path = s.recv(path_len)
            path = os.path.abspath(path)
            if os.path.abspath(path) == os.path.abspath(CONFIG_PATH):
                found = True
                break
        except:
            break
    
    if found:
        if args:
            cmd = "activate " + os.getcwd() + "\0" + "\0".join(args)
            encoded = struct.pack(">h", len(cmd)) + cmd
            s.send(encoded)
            time.sleep(0.5)   # don't close socket immediately
        return True

    return False

port = -1
try:
    f = open(os.path.join(CONFIG_PATH, 'port'))
    port = int(f.read())
except Exception:
    type, value, traceback = sys.exc_info()
    print(value)
    port = -1

if port == -1:
    # SocketLock actually allows up to 50 ports, but the checking takes too long
    for port in range(6942, 6942+10):
        if launch_with_port(port): exit()
else:
    if launch_with_port(port): exit()

if sys.platform == "darwin":
    # Mac OS: RUN_PATH is *.app path
    if len(args):
        args.insert(0, "--args")
    os.execvp("open", ["-a", RUN_PATH] + args)
else:
    # unix common
    bin_dir, bin_file = os.path.split(RUN_PATH)
    os.chdir(bin_dir)
    os.execv(bin_file, [bin_file] + args)
