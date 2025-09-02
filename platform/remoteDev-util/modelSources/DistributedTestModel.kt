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
    field("productType", RdProductType)
    field("testIdeProductCode", string)
    field("testQualifiedClassName", string)
    field("testMethodNonParameterizedName", string)
    field("testMethodParametersArrayString", string)
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

  private val RdTestSessionLightException = structdef {
    field("type", string)
    field("message", string.nullable)
    field("stacktrace", immutableList(RdTestSessionStackTraceElement))
  }

  private val RdTestSessionException = structdef {
    field("type", string)
    field("originalType", string.nullable)
    field("message", string.nullable)
    field("stacktrace", immutableList(RdTestSessionStackTraceElement))
    field("cause", RdTestSessionLightException.nullable)
    field("suppressedExceptions", immutableList(RdTestSessionLightException).nullable)
  }


  private val RdTestActionParameters = structdef {
    field("title", string)
    field("parameters", immutableList(string).nullable)
  }

  private val RdTestComponentData = structdef {
    field("width", int)
    field("height", int)
  }

  private val RdTestSession = classdef {
    field("rdAgentInfo", RdAgentInfo)
    field("runTestMethod", bool)
    field("traceCategories", immutableList(string))
    field("debugCategories", immutableList(string))
    property("ready", bool.nullable)
    signal("sendException", RdTestSessionException).async
    signal("exitApp", void).async
    signal("showNotification", string)
    call("forceLeaveAllModals", bool, void).async
    call("closeAllOpenedProjects", void, bool).async
    call("runNextAction", RdTestActionParameters, string.nullable).async
    call("requestFocus", bool, bool).async
    call("isFocused", void, bool).async
    call("visibleFrameNames", void, immutableList(string)).async
    call("projectsNames", void, immutableList(string)).async
    call("makeScreenshot", string, bool).async
    call("isResponding", void, bool).async
    call("projectsAreInitialised", void, bool).async
    call("getProductCodeAndVersion", void, RdProductInfo).async
  }

  private val RdProductInfo = structdef {
    field("productCode", string)
    field("productVersion", string)
  }

  init {
    property("session", RdTestSession.nullable)
  }
}
