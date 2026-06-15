---
name: Codex IDE Context
description: Requirements for Codex /ide IPC transport and editor context payloads.
targets:
  - ../../codex/ide/src/**/*.kt
  - ../../codex/ide/testSrc/**/*.kt
---

# Codex IDE Context

Status: Draft
Date: 2026-05-09

## Summary
Define the Codex `/ide` IPC bridge used by Codex TUI to request active IDE editor context from Agent Workbench.

## Requirements
- Codex TUI `/ide` context must be served by an Agent Workbench IPC provider compatible with Codex `ide-context` requests: Unix socket
  `${java.io.tmpdir}/codex-ipc/ipc-<uid>.sock` on macOS/Linux, named pipe `\\.\pipe\codex-ipc` on Windows, 4-byte little-endian
  frame length, and JSON `request`/`response` payloads. Requests are scoped by `params.workspaceRoot`; missing or non-open workspaces
  return `no-client-found`, unsupported methods return `no-handler-for-request`, and unsupported versions return `request-version-mismatch`.
  Malformed clients must not stop the IPC provider or block other clients. Unix clients may remain idle long-term without being closed solely
  for idleness, while incomplete frames are still timed out. Unix socket files and their `codex-ipc` parent directory
  must be owner-only so Codex accepts the provider before connecting.
  [@test] ../../codex/ide/testSrc/CodexIdeContextIpcProtocolTest.kt
  [@test] ../../codex/ide/testSrc/CodexIdeContextUnixIpcTransportTest.kt
  [@test] ../../codex/ide/testSrc/CodexIdeContextWindowsIpcTransportTest.kt

- Codex `/ide` context payloads must include active editor file metadata, zero-based selections, active selection content only for a single
  selected range, and workspace-relative open tab paths while excluding Agent Workbench chat virtual files.
  [@test] ../../codex/ide/testSrc/CodexIdeContextCollectorTest.kt
