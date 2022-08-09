#!/bin/bash
function e {
  if [ -z "$1" ]; then
    echo "Missing argument, pass path to file as first argument"
    exit 1
  fi

  if [ -e "$1" ]; then
    echo "Formatting file $1"
  else
    echo "No such file"
    exit 1
  fi

  path="testResources/after-formatting/${1#*/*/}"
  dir="$(dirname "${path}")"
  mkdir -p "$dir"
  cp "$1" "$path"

  python3 black.py --line-length 5000 "$path"
  python3 comments.py "$path"
  python3 black.py --use-tabs --line-length 5000 "$path"
}
export -f e

find testResources/before-formatting -type f -name "*.py" -exec bash -c 'e "$0"' {} \;
