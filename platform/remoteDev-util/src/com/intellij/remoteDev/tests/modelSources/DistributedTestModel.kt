package com.intellij.remoteDev.tests.modelSources

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*

/**
 * Model to bind test process and an IDE agent (client or server)
 */
object TestRoot : Root()

@Suppress("unused")
object DistributedTestModel : Ext(TestRoot) {

  private val RdAgentId = structdef {
    field("id", string)
    field("launchNumber", int)
  }

  private val RdTestSessionStackTraceElement = structdef {
    field("declaringClass", string)
    field("methodName", string)
    field("fileName", string)
    field("lineNumber", int)
  }

  private val RdTestSessionExceptionCause = structdef {
    field("type", string)
    field("message", string.nullable)
    field("stacktrace", immutableList(RdTestSessionStackTraceElement))
  }

  private val RdTestSessionException = structdef {
    field("type", string)
    field("message", string.nullable)
    field("stacktrace", immutableList(RdTestSessionStackTraceElement))
    field("cause", RdTestSessionExceptionCause.nullable)
  }

  private val RdTestSession = classdef {
    field("agentId", RdAgentId)
    field("testClassName", string.nullable)
    field("testMethodName", string.nullable)
    field("traceCategories", immutableList(string))
    property("ready", bool.nullable)
    signal("sendException", RdTestSessionException).async
    signal("shutdown", void)
    signal("dumpThreads", void).async
    call("closeProject", void, bool)
    call("closeProjectIfOpened", void, bool)
    call("runNextAction", void, bool)
    call("makeScreenshot", string, bool)
  }

  init {
    property("session", RdTestSession.nullable)
  }
}
