package com.intellij.remoteDev.tests.modelSources

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*

/**
 * Model to bind test process and an IDE ide (client or server)
 */
object LambdaTestRoot : Root()

@Suppress("unused")
object LambdaTestModel : Ext(LambdaTestRoot) {

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
    field("testClass", string)
    field("testMethod", string)
    field("methodArgumentssBase64", immutableList(string))
  }

  private val LambdaRdSerialized = structdef {
    field("stepName", string)
    field("serializedDataBase64", string)
    field("classPath", immutableList(string))
    field("parametersBase64", immutableList(string))
  }

  private val LambdaRdTestSession = classdef {
    field("rdIdeType", LambdaRdIdeType)
    property("ready", bool.nullable)
    signal("sendException", LambdaRdTestSessionException).async
    call("runLambda", LambdaRdTestActionParameters, void).async
    call("runSerializedLambda", LambdaRdSerialized, string).async
    call("beforeEach", string, void).async
    call("beforeAll", string, void).async
    call("afterEach", string, void).async
    call("afterAll", string, void).async
    call("isResponding", void, bool).async
  }

  init {
    property("session", LambdaRdTestSession.nullable)
  }
}
