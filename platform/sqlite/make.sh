#!/usr/bin/env bash
# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
set -ex

outDir="target/sqlite/$OS-$ARCH"
rm -rf "${outDir:?}/*"
mkdir -p "$outDir"

# brew install llvm
# use latest CLang 15 instead of 14 for a smaller binaries
export PATH="/opt/homebrew/opt/llvm/bin:$PATH"
export LDFLAGS="-L/opt/homebrew/opt/llvm/lib"
export CPPFLAGS="-I/opt/homebrew/opt/llvm/include"

cFlags="-O3 -fPIC -Isqlite -fvisibility=hidden -Wno-implicit-function-declaration"
linkFlags="-Wl,-S,-x"
libFilename="so"
if [ "$OS" == "mac" ]; then
  cFlags+=" -mmacosx-version-min=10.14"
  linkFlags="-dynamiclib -fuse-ld=lld "
  libFilename="libsqliteij.jnilib"

  if [ "$ARCH" == "x86_64" ]; then
    cFlags+=" --target=x86_64-apple-darwin18.7.0"
  fi
elif [ "$OS" == "linux" ]; then
  libFilename="libsqliteij.so"

  # cannot compile arm - unable to find library -lgcc, so, use dock cross
  if [ "$ARCH" == "aarch64" ]; then
    linkFlags+=" -shared"
  else
    cFlags+=" --target=$ARCH-unknown-linux-gnu --sysroot=target/linux-$ARCH"
    linkFlags+=" -shared -fuse-ld=lld"
  fi
elif [ "$OS" == "win" ]; then
  linkFlags="-Wl,--kill-at -shared -static-libgcc"
  libFilename="sqliteij.dll"
fi

CC="${CC:-clang}"

#linkFlags+=" -fuse-ld=lld"
"${CROSS_PREFIX}${CC}" -o "$outDir/sqlite3.o" -c $cFlags \
  -DSQLITE_DQS=1 \
  -DSQLITE_THREADSAFE=1 \
  -DSQLITE_DEFAULT_MEMSTATUS=0 \
  -DSQLITE_DEFAULT_WAL_SYNCHRONOUS=1 \
  -DSQLITE_LIKE_DOESNT_MATCH_BLOBS \
  -DSQLITE_MAX_EXPR_DEPTH=0 \
  -DSQLITE_OMIT_DECLTYPE \
  -DSQLITE_OMIT_DEPRECATED \
  -DSQLITE_OMIT_PROGRESS_CALLBACK \
  -DSQLITE_OMIT_SHARED_CACHE \
  -DSQLITE_USE_ALLOCA \
  -DSQLITE_OMIT_AUTOINIT \
  -DSQLITE_HAVE_ISNAN \
  -DHAVE_USLEEP=1 \
  -DSQLITE_TEMP_STORE=2 \
  -DSQLITE_DEFAULT_CACHE_SIZE=2000 \
  -DSQLITE_CORE \
  -DSQLITE_ENABLE_FTS5 \
  -DSQLITE_ENABLE_STAT4 \
  -DSQLITE_MAX_MMAP_SIZE=1099511627776 \
  \
  sqlite/sqlite3.c

"${CROSS_PREFIX}${CC}" -o "$outDir/NativeDB.o" -c $cFlags -I"sqlite/$OS" sqlite/NativeDB.c

libFile="$outDir/$libFilename"
"${CROSS_PREFIX}${CC}" $cFlags -o "$libFile" "$outDir/NativeDB.o" "$outDir/sqlite3.o" $linkFlags
shasum -a 256 "$libFile" | head -c 64 >"$libFile.sha256"

unlink "$outDir/sqlite3.o"
unlink "$outDir/NativeDB.o"
