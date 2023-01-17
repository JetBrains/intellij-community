#!/usr/bin/env bash
# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
set -ex

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
cd "$SCRIPT_DIR"

createSysRoot() {
  outDir="target/linux-$1"
  if [ -d "$outDir" ]; then
    echo "Sysroot $outDir exists, skip creating"
    return
  fi

  imageArch=""
  if [ "$1" == "x86_64" ]; then
    imageArch=amd64
  elif [ "$1" == "aarch64" ]; then
    imageArch=arm64v8
  fi

  mkdir -p "$outDir"
  # use old glibc (2.24)
  nerdctl save --platform linux/$imageArch -o "$outDir.tar" buildpack-deps:bionic
  python3 docker-image-extract.py "$outDir.tar" "$outDir"
  unlink "$outDir.tar"
}

createSysRoot x86_64
#createSysRoot aarch64

# echo "deb http://archive.ubuntu.com/ubuntu/ jammy-proposed universe" | tee /etc/apt/sources.list.d/docker.list

# apt-get update && apt install -y install clang lldb lld
#nerdctl run -ti -v "$SCRIPT_DIR":/work silkeh/clang@sha256:693fdfa16424b2f41408204933b63a796e2700f1865a19c7eec7f6606040d7fd

#OS=mac ARCH=aarch64 ./make.sh
#OS=mac ARCH=x86_64 ./make.sh
#OS=linux ARCH=aarch64 ./make.sh
#OS=linux ARCH=x86_64 ./make.sh
#
#nerdctl run --rm --platform linux/amd64 -v "$SCRIPT_DIR":/work --workdir=/work \
#  dockcross/windows-static-x64 bash -c 'OS=win ARCH=x86_64 CC=gcc CROSS_PREFIX=x86_64-w64-mingw32.static- ./make.sh'
#
#nerdctl run --rm --platform linux/amd64 -v "$SCRIPT_DIR":/work --workdir=/work \
#  dockcross/windows-arm64 bash -c 'OS=win ARCH=aarch64 CC=clang CROSS_PREFIX=aarch64-w64-mingw32- ./make.sh'

nerdctl run --rm --platform linux/amd64 -v "$SCRIPT_DIR":/work --workdir=/work \
  dockcross/linux-arm64 bash -c 'OS=linux ARCH=aarch64 CC=gcc CROSS_PREFIX=aarch64-unknown-linux-gnu- ./make.sh'
