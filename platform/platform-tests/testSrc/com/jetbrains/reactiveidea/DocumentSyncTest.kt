/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.reactiveidea

/*
* Copyright 2000-2015 JetBrains s.r.o.
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


import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.ReactiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import kotlin.test.assertEquals

public class DocumentSyncTest : LightPlatformCodeInsightFixtureTestCase() {
  public fun testSimpleDocumentSync() {
    var modelRef: ReactiveModel? = null
    val mirror = ReactiveModel(Lifetime.Eternal) { diff ->
      modelRef!!.performTransaction { m ->
        m.patch(diff)
      }
    }

    val model = ReactiveModel(Lifetime.Eternal) { diff ->
      mirror.performTransaction { m ->
        m.patch(diff)
      }
    }
    modelRef = model

    val first = DocumentImpl("my test document")
    val firstDocumentHost = DocumentHost(Lifetime.Eternal, model, Path("document"), first)

    val second = DocumentImpl("")
    val secondDocumentHost = DocumentHost(Lifetime.Eternal, mirror, Path("document"), second)

    first.insertString(0, "hello world!\n")
    first.insertString(0, "abcd\n")
    first.insertString(0, "fuck!\n")

    second.insertString(0, "blabla")

    assertEquals(first.getText(), second.getText())
  }
}