# Script for EEL local execution test
# 1.prints tty and its size
# 2.waits for command exit (exit 0) or sleep (sleep 10_000)
# 3. installs signal for SIGINT to return 42
import os
import signal
import sys
import json
from time import sleep


def exit_42(*_):
  exit(42)

signal.signal(signal.SIGINT, exit_42)

is_tty = sys.stdin.isatty()
terminal_size = None

try:
  terminal_size = os.get_terminal_size()
except OSError:
  pass


sys.stderr.write("hello\n")
sys.stderr.flush()

json.dump({
  "tty": is_tty,
  "size": {"cols": terminal_size.columns, "rows":terminal_size.lines} if terminal_size else None
}, sys.stdout)
sys.stdout.flush()


command = input().strip()
if command == "exit":
  exit(0)
elif command == "sleep":
  print("sleeping")
  sys.stdout.flush()
  sleep(10_000)
