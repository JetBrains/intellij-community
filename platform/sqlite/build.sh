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
    imageArch=arm64/v8
  fi

  nerdctl pull --platform linux/$imageArch buildpack-deps:bionic

  mkdir -p "$outDir"
  # use old glibc (2.24)
  nerdctl save --platform linux/$imageArch -o "$outDir.tar" buildpack-deps:bionic
  python3 docker-image-extract.py "$outDir.tar" "$outDir"
  unlink "$outDir.tar"
}

createSysRoot x86_64
#createSysRoot aarch64

OS=mac ARCH=aarch64 ./make.sh &
OS=mac ARCH=x86_64 ./make.sh &
OS=linux ARCH=aarch64 ./make.sh &
OS=linux ARCH=x86_64 ./make.sh &

nerdctl run --rm --platform linux/amd64 -v "$SCRIPT_DIR":/work --workdir=/work \
  dockcross/windows-static-x64@sha256:8a53628099d9ce085303aa962120cd45b8f7a2b58b86fefc9797e9cbf43ce906 bash -c 'OS=win ARCH=x86_64 CC=gcc CROSS_PREFIX=x86_64-w64-mingw32.static- ./make.sh' &

nerdctl run --rm --platform linux/amd64 -v "$SCRIPT_DIR":/work --workdir=/work \
  dockcross/windows-arm64@sha256:345e3c190fbdf44384ce256dd09f5ca9ace831a5344308c8dccb36b78c382d95 bash -c 'OS=win ARCH=aarch64 CC=clang CROSS_PREFIX=aarch64-w64-mingw32- ./make.sh' &

nerdctl run --rm --platform linux/amd64 -v "$SCRIPT_DIR":/work --workdir=/work \
  dockcross/linux-arm64-lts@sha256:3bbb880b002f6cc1b5332719bbb0c2ba5c646260140c2ff15bff8d63a16187ba bash -c 'OS=linux ARCH=aarch64 CC=gcc CROSS_PREFIX=aarch64-unknown-linux-gnu- ./make.sh' &

wait