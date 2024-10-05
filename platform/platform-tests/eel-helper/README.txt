Helper for EEL local execution test
1. prints hello into stderr
2. prints tty and its size to stdout
3. waits for command exit (exit 0) or sleep (sleep 15_000)
4. installs signal for SIGINT to return 42