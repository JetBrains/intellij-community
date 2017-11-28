/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.remote.transport

import org.junit.runner.Description
import java.io.Serializable

/**
 * @author Sergey Karashevich
 */
enum class Type {RUN_STARTED, STARTED, ASSUMPTION_FAILURE, RUN_FINISHED, FAILURE, FINISHED, IGNORED }

data class JUnitInfo(val type: Type, val obj: Any?, val testClassAndMethodName: String) : Serializable {
  companion object {
    fun getClassAndMethodName(description: Description) = "${description.className}#${description.methodName}"
  }
}
