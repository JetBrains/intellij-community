#!/usr/bin/env bash
# Creates a decision record in this folder from the template and prints its path.
# The id is the number of seconds from ADR_ID_BASE to now, in uppercase base 36, padded to six characters.
# The rule is in ../AGENTS.md, section "Decision records". ADR_ID_BASE in
# ../test/src/com/intellij/platform/settings/v2/docs/adrFiles.kt holds the same base. SettingsDocsTest compares both bases
# and checks each id.
# Usage: new-adr.sh "<decision as a sentence>"
set -euo pipefail

# 2026-11-18T05:20:25Z
ADR_ID_BASE=1794979225
ADR_ID_ALPHABET=0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ
ADR_ID_LENGTH=6

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 \"<decision as a sentence>\"" >&2
  exit 2
fi

title=$1
adr_dir=$(cd "$(dirname "$0")" && pwd)

adr_id() {
  local seconds=$1 id=
  for ((i = 0; i < ADR_ID_LENGTH; i++)); do
    id=${ADR_ID_ALPHABET:seconds % 36:1}$id
    seconds=$((seconds / 36))
  done
  echo "$id"
}

# Prints the UTC time of the epoch second $1, such as 2026-11-18T05:20:25Z, with GNU date or with BSD date.
utc_time() {
  date -u -d "@$1" +%FT%TZ 2> /dev/null || date -u -r "$1" +%FT%TZ 2> /dev/null
}

# A numbered record NNNN holds the id 00NNNN. A fold entry "### <id>," holds an id too.
adr_id_is_taken() {
  local id=$1
  compgen -G "$adr_dir/$id-*.md" > /dev/null && return 0
  grep -qs "^### $id," "$adr_dir"/*.md && return 0
  [[ $id =~ ^00[0-9]{4}$ ]] || return 1
  compgen -G "$adr_dir/${id:2}-*.md" > /dev/null || grep -qs "^### ${id:2}," "$adr_dir"/*.md
}

slug=$(printf '%s' "$title" | LC_ALL=C tr '[:upper:]' '[:lower:]' | LC_ALL=C sed -E "s/'//g; s/[^a-z0-9]+/-/g; s/^-+|-+$//g")
if [[ -z $slug ]]; then
  echo "The title \"$title\" has no ASCII letter or digit, so it gives no file name." >&2
  exit 2
fi

seconds=$(($(date -u +%s) - ADR_ID_BASE))
while adr_id_is_taken "$(adr_id "$seconds")"; do
  seconds=$((seconds + 1))
done
id=$(adr_id "$seconds")
if ! created=$(utc_time "$((ADR_ID_BASE + seconds))"); then
  echo "Neither 'date -u -d' nor 'date -u -r' formats the time of the id $id." >&2
  exit 1
fi
file="$adr_dir/$id-$slug.md"

cat > "$file" <<TEMPLATE
# $id. $title

Date: $created

## Status

Proposed.

## Context

## Decision

## Consequences

## Rejected alternatives
TEMPLATE

echo "$file"
