#!/usr/bin/env bash
# Rebuilds source-project/ and copies the three .table fixtures next to this script
# Requires Gradle 8+ on PATH
set -euo pipefail

THIS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

(cd "$THIS_DIR/source-project" && gradle --no-daemon clean assemble)

cp "$THIS_DIR/source-project/build/kotlin/compileKotlin/cacheable/cri"/{lookups,fileIdsToPaths,subtypes}.table "$THIS_DIR/"
echo "Regenerated fixtures in $THIS_DIR"
