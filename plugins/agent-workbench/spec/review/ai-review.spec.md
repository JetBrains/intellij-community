---
name: AI Review
description: Requirements for AI Review sessions, prompt integration, Problems toolwindow tabs, and ACP-backed execution.
targets:
  - ../../ai-review/src/**/*.kt
  - ../../ai-review/resources/**/*.xml
  - ../../ai-review/resources/messages/*.properties
  - ../../ai-review/testSrc/**/*.kt
---

# AI Review

Status: Draft
Date: 2026-05-09

## Summary
AI Review creates independent review sessions shown as Problems toolwindow tabs. The community module owns session/UI models and prompt palette integration; ultimate modules provide Space actions and ACP-agent execution.

## Requirements
- The community AI Review module remains free of ACP and AI Assistant dependencies; ACP execution lives in the agents module and Space posting lives in the Space bridge module.

- Each review session has an independent view model, problem holder, Problems tab, lifecycle state, and close/cancel behavior.
  [@test] ../../ai-review/testSrc/model/AIReviewViewModelTest.kt

- Creating a review session adds and selects a dynamic Problems tab. Removing a session cancels running work and removes the tab; cancelling a running review leaves the tab visible with partial results.

- The prompt palette extension is available for VCS commits or Changes tree context and submits through `AIReview.AgentWorkbench.ExecuteAction`.
  [@test] ../../ai-review/testSrc/prompt/AIReviewPaletteExtensionTest.kt

- Agent selection maps Agent Workbench providers to ACP agents where available: Claude Code, Codex, and Junie.

- ACP execution regenerates the AI Review ACP config, runs through `EelAcpProcessHandler`, streams incremental problems, and runs a final reconciliation parse after completion.

- ACP tool calls are auto-approved for read/search/execute categories and rejected for edit/delete/move categories.

- Problems display groups findings by file, supports severity filtering, preserves partial results on error/cancel, and exposes per-session feedback/export actions.
  [@test] ../../ai-review/testSrc/model/AIReviewViewModelTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.ai.review.tests --test com.intellij.agent.workbench.ai.review.prompt.AIReviewPaletteExtensionTest`
- `./tests.cmd --module intellij.agent.workbench.ai.review.tests --test com.intellij.agent.workbench.ai.review.model.AIReviewViewModelTest`
- `./bazel.cmd build @community//plugins/agent-workbench/ai-review:agent-workbench-ai-review`
- `./bazel.cmd build //plugins/agent-workbench/ai-review-agents:agent-workbench-ai-review-agents`
- `./bazel.cmd build //plugins/agent-workbench/ai-review-space:agent-workbench-ai-review-space`

## References
- `../actions/global-prompt-entry.spec.md`
- `../prompt-context/prompt-context-vcs.spec.md`
