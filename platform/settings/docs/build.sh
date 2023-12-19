#!/usr/bin/env bash
# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
set -e -u -x

BASEDIR=$(dirname "$0")
cd "$BASEDIR/../../../../out" || exit

rm -rf settings-doc-draf/
mkdir -p settings-doc-draf/site

cd settings-doc-draf
unzip ../webHelpSETTINGS-DOCS-DRAFT2-all.zip -d site

cp "$BASEDIR"/Dockerfile ./
cp "$BASEDIR"/nginx.conf ./

nerdctl build --platform=amd64,arm64 . -t registry.jetbrains.team/p/ij/structurizr/settings-docs:latest
nerdctl push --platform=amd64,arm64 registry.jetbrains.team/p/ij/structurizr/settings-docs:latest

echo "$PWD"