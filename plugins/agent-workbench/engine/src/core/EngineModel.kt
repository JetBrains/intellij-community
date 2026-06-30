// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.agent.workbench.engine.core

import kotlinx.serialization.Serializable

/**
 * Lifecycle status of a thread, derived from the event stream by the reducer.
 * See the Engine design, section 8.2.
 */
@Serializable
enum class ThreadStatus {
  Draft,
  Preparing,
  Starting,
  Running,
  WaitingForApproval,
  WaitingForUser,
  Paused,
  Stopping,
  Completed,
  Failed,
  Cancelled,
  Disconnected,
  Archived,
}

/** Kind of structured runtime adapter that produces a thread's event stream. */
@Serializable
enum class RuntimeKind {
  /** Structured terminal content, debug mirrors, or migration experiments, not existing Agent Thread View CLI providers. */
  Terminal,
  StructuredTerminal,
  Acp,
  Remote,
  Mock,
}

/**
 * Where a thread's working tree lives. Multi-level by design: from no isolation
 * (the user runs agents in their own folders) up to remote workspaces.
 */
@Serializable
enum class IsolationMode {
  /** No isolation: shared working tree; concurrent agents may overlap; attribution is approximate. */
  SharedWorkingTree,

  /** Bind to a git worktree the user already created. */
  ExistingWorktree,

  /** Create a dedicated git worktree per thread on the fly. */
  GitWorktreeOnTheFly,

  /** Docker container with overlay filesystem. */
  Container,

  /** Remote/cloud-owned workspace (architectural headroom; not implemented yet). */
  RemoteWorkspace,
}

/** Capabilities a runtime may exercise, gated by the permission layer. */
@Serializable
enum class AgentCapability {
  Terminal,
  StructuredEvents,
  AcpSession,
  ReadFile,
  WriteFile,
  ApplyPatch,
  RunCommand,
  NetworkAccess,
  InstallDependency,
  ModifyGit,
  CreateBranch,
  PushBranch,
  CreatePr,
  AccessSecrets,
  AccessEnv,
  InspectProject,
  ReadDiagnostics,
  CreateCheckpoint,
  ReportPlan,
  ReportTests,
}

/** Who produced an event. */
@Serializable
enum class EventSource {
  User,
  Agent,
  Runtime,
  Ide,
  Vcs,
  Policy,
  System,
}

/** Audience of an event in the UI. */
@Serializable
enum class EventVisibility {
  User,
  Debug,
  AuditOnly,
}

/** How a thread should be stopped. */
enum class StopMode {
  Graceful,
  Interrupt,
  Terminate,
  Kill,
  Detach,
}
