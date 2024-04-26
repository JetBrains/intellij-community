#!/usr/bin/env bash
# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
set -e -u -x

BASEDIR=$(dirname "$0")
cd "$BASEDIR/../../../../out" || exit

rm -rf settings-doc-draft/
mkdir -p settings-doc-draft/site

cd settings-doc-draft
unzip ../webHelpSETTINGS-DOCS-DRAFT2-all.zip -d site

cp "$BASEDIR"/Dockerfile ./
cp "$BASEDIR"/nginx.conf ./

docker build --platform=linux/amd64 . -t registry.jetbrains.team/p/ij/structurizr/settings-docs:latest
docker push registry.jetbrains.team/p/ij/structurizr/settings-docs:latest

echo "$PWD"

# docker doesn't have D2
#OUT_DIR="$BASEDIR/../../../../out/settings-doc-draft"
#rm -rf "$OUT_DIR"
#mkdir -p "$OUT_DIR"
#
## see latest version in https://jetbrains.team/p/writerside/packages/container/builder/writerside-builder
#docker run --rm -v .:/opt/sources -v "$OUT_DIR":/opt/out  registry.jetbrains.team/p/writerside/builder/writerside-builder:2.1.1755-p5358 /bin/bash -c "
#export DISPLAY=:99 &&
#Xvfb :99 &
#/opt/builder/bin/idea.sh helpbuilderinspect \
#--source-dir /opt/sources \
#--product Writerside/settings-docs-draft \
#--runner other \
#--output-dir /opt/out
#"
#
#cd "$OUT_DIR"