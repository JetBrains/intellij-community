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

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.util.ui.UIUtil
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.util.Lifetime

public class DocumentsSynchronizer(val project: Project): ProjectComponent {
  val lifetime = Lifetime.create(Lifetime.Eternal)
  var bJavaHost: DocumentHost? = null
  var aTxtHost: DocumentHost? = null


  override fun getComponentName(): String = "DocumentsSynchronizer"

  override fun initComponent() {
    UIUtil.invokeLaterIfNeeded {
      val aTxt = StandardFileSystems.local().findFileByPath("/Users/jetzajac/IdeaProjects/untitled/src/A.txt")
      val aTxtDoc = FileDocumentManager.getInstance().getDocument(aTxt!!)

      serverModel(lifetime.lifetime, 12346) { m ->
        aTxtHost = DocumentHost(lifetime.lifetime, m, Path("document"), aTxtDoc!!)
      }

      val clientModel = clientModel("http://localhost:12346", Lifetime.Eternal)

      val bJava = StandardFileSystems.local().findFileByPath("/Users/jetzajac/IdeaProjects/untitled/src/B.java")
      val bTxtDoc = FileDocumentManager.getInstance().getDocument(bJava!!)
      bJavaHost = DocumentHost(lifetime.lifetime, clientModel, Path("document"), bTxtDoc!!)
    }
  }

  override fun disposeComponent() {
    lifetime.terminate()
  }

  override fun projectOpened() {

  }

  override fun projectClosed() {

  }

}
