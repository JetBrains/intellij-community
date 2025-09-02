package com.intellij.remoteDev.tests.modelSources

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*

/**
 * Model to bind test process and an IDE ide (client or server)
 */
object LambdaTestRoot : Root()

@Suppress("unused")
object LambdaTestModel : Ext(LambdaTestRoot) {

  private val LambdaRdIdeInfo = structdef {
    field("id", string)
    field("ideType", LambdaRdIdeType)
  }

  private val LambdaRdIdeType = enum {
    +"BACKEND"
    +"FRONTEND"
    +"MONOLITH"
  }

  private val LambdaRdTestSessionStackTraceElement = structdef {
    field("declaringClass", string)
    field("methodName", string)
    field("fileName", string)
    field("lineNumber", int)
  }

  private val LambdaRdTestSessionExceptionCause = structdef {
    field("type", string)
    field("message", string.nullable)
    field("stacktrace", immutableList(LambdaRdTestSessionStackTraceElement))
  }

  private val LambdaRdTestSessionException = structdef {
    field("type", string)
    field("originalType", string.nullable)
    field("message", string.nullable)
    field("stacktrace", immutableList(LambdaRdTestSessionStackTraceElement))
    field("cause", LambdaRdTestSessionExceptionCause.nullable)
  }


  private val LambdaRdTestActionParameters = structdef {
    field("reference", string)
    field("parameters", immutableList(string).nullable)
  }

  private val LambdaRdTestSession = classdef {
    field("rdIdeInfo", LambdaRdIdeInfo)
    property("ready", bool.nullable)
    signal("sendException", LambdaRdTestSessionException).async
    call("closeAllOpenedProjects", void, bool).async
    call("runLambda", LambdaRdTestActionParameters, void).async
    call("requestFocus", bool, bool).async
    call("isFocused", void, bool).async
    call("visibleFrameNames", void, immutableList(string)).async
    call("projectsNames", void, immutableList(string)).async
    call("makeScreenshot", string, bool).async
    call("isResponding", void, bool).async
    call("projectsAreInitialised", void, bool).async
  }

  init {
    property("session", LambdaRdTestSession.nullable)
  }
}
