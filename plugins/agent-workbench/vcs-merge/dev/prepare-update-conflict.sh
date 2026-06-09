#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: bash prepare-update-conflict.sh <target-git-project> [conflict-file]

Prepares a local Git repository so IDE Update Project stops on a merge conflict.

The script creates a local bare origin under the target repository's .git directory,
pushes a remote commit, then creates a conflicting local commit on the current branch.
It is intended for manual testing of Agent Workbench merge-conflict entry points.

Example:
  bash community/plugins/agent-workbench/vcs-merge/dev/prepare-update-conflict.sh \
    /Users/develar/IdeaProjects/untitled2
EOF
}

if [[ $# -lt 1 || $# -gt 2 || "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 1
fi

repo_dir="$1"
conflict_file="${2:-src/UpdateConflictDemo.txt}"

if [[ "$conflict_file" = /* || "$conflict_file" == *..* ]]; then
  echo "conflict-file must be a repository-relative path without '..': $conflict_file" >&2
  exit 1
fi

cd "$repo_dir"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not a Git repository: $repo_dir" >&2
  exit 1
fi

branch="$(git symbolic-ref --quiet --short HEAD || true)"
if [[ -z "$branch" ]]; then
  echo "The repository is in detached HEAD state. Checkout a branch first." >&2
  exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Tracked changes are present. Commit, stash, or revert them before preparing the conflict." >&2
  git status --short
  exit 1
fi

if [[ -e "$conflict_file" ]] && ! git ls-files --error-unmatch "$conflict_file" >/dev/null 2>&1; then
  echo "Refusing to overwrite untracked file: $conflict_file" >&2
  exit 1
fi

remote_dir="$repo_dir/.git/update-conflict-origin.git"
origin_url="$(git remote get-url origin 2>/dev/null || true)"
if [[ -n "$origin_url" && "$origin_url" != "$remote_dir" ]]; then
  echo "A non-script origin remote is already configured: $origin_url" >&2
  echo "This script only manages its own local origin at: $remote_dir" >&2
  exit 1
fi

tmp_clone="$(mktemp -d "${TMPDIR:-/tmp}/update-conflict-remote.XXXXXX")"
cleanup() {
  rm -rf "$tmp_clone"
}
trap cleanup EXIT

git remote remove origin >/dev/null 2>&1 || true
rm -rf "$remote_dir"

mkdir -p "$(dirname "$conflict_file")"
cat > "$conflict_file" <<'EOF'
This file is created by prepare-update-conflict.sh.
Update Project should report a conflict on the next line.
value=base
EOF

git add "$conflict_file"
git -c user.name="Update Conflict Script" \
    -c user.email="update-conflict@example.invalid" \
    commit -m "Prepare update conflict base"

git init --bare "$remote_dir" >/dev/null
git remote add origin "$remote_dir"
git push -u origin "$branch"

git clone --quiet "$remote_dir" "$tmp_clone"
git -C "$tmp_clone" checkout --quiet "$branch"
cat > "$tmp_clone/$conflict_file" <<'EOF'
This file is created by prepare-update-conflict.sh.
Update Project should report a conflict on the next line.
value=remote-change
EOF
git -C "$tmp_clone" add "$conflict_file"
git -C "$tmp_clone" \
    -c user.name="Update Conflict Script" \
    -c user.email="update-conflict@example.invalid" \
    commit -m "Remote update conflict change"
git -C "$tmp_clone" push --quiet origin "$branch"

cat > "$conflict_file" <<'EOF'
This file is created by prepare-update-conflict.sh.
Update Project should report a conflict on the next line.
value=local-change
EOF
git add "$conflict_file"
git -c user.name="Update Conflict Script" \
    -c user.email="update-conflict@example.invalid" \
    commit -m "Local update conflict change"

cat <<EOF

Prepared an Update Project conflict in: $repo_dir/$conflict_file
Current branch '$branch' now tracks local remote 'origin'.
Run Update Project in the IDE. It should stop with a conflict in $conflict_file.
EOF
