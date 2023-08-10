package com.intellij.remoteDev.tests.modelSources

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*

/**
 * Model to bind test process and an IDE agent (client or server)
 */
object TestRoot : Root()

@Suppress("unused")
object DistributedTestModel : Ext(TestRoot) {

  private val RdAgentInfo = structdef {
    field("id", string)
    field("launchNumber", int)
    field("agentType", RdAgentType)
    field("productTypeType", RdProductType)
  }

  private val RdAgentType = enum {
    +"HOST"
    +"CLIENT"
    +"GATEWAY"
  }

  private val RdProductType = enum {
    +"REMOTE_DEVELOPMENT"
    +"CODE_WITH_ME"
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
    field("originalType", string.nullable)
    field("message", string.nullable)
    field("stacktrace", immutableList(RdTestSessionStackTraceElement))
    field("cause", RdTestSessionExceptionCause.nullable)
  }

  private val RdTestSession = classdef {
    field("agentInfo", RdAgentInfo)
    field("testClassName", string.nullable)
    field("testMethodName", string.nullable)
    field("traceCategories", immutableList(string))
    field("debugCategories", immutableList(string))
    property("ready", bool.nullable)
    signal("sendException", RdTestSessionException).async
    signal("shutdown", void)
    call("closeProject", void, bool)
    call("closeProjectIfOpened", void, bool)
    call("runNextAction", void, string.nullable)
    call("runNextActionBackground", void, string.nullable)
    call("makeScreenshot", string, bool)
    call("isResponding", void, bool)
  }

  init {
    property("session", RdTestSession.nullable)
  }
}
