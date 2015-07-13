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
package com.jetbrains.reactivemodel

import com.jetbrains.reactivemodel.models.AbsentModel
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class IndexTest {

  Test
  fun testEditorIndex() {
    val model = ReactiveModel()
    val originalPath = Path("a", "b", "c")
    val editor1Path = originalPath / "edt1"
    val lifetime = Lifetime.Eternal
    val editorSignal = model.subscribe(lifetime, editorsTag)

    val counter = AtomicInteger()

    reaction(true, "simple signal", editorSignal) { editors ->
      counter.incrementAndGet()
    }

    // first editor added
    model.transaction { m ->
      var mdl = editor1Path.putIn(m, MapModel())
      mdl = (editor1Path / tagsField).putIn(mdl, ListModel(arrayListOf(PrimitiveModel("editor"))))
      mdl
    }


    model.transaction { m ->
      val model1 = editor1Path.getIn(m) as MapModel
      assertTrue(editorsTag.getIn(m).contains(model1))
      m
    }


    // second editor added
    val editor2Path = originalPath / "edt2"
    model.transaction { m ->
      var mdl = editor2Path.putIn(m, MapModel())
      mdl = (editor2Path / tagsField).putIn(mdl, ListModel(arrayListOf(PrimitiveModel("editor"))))
      mdl
    }

    model.transaction { m ->
      val model1 = editor1Path.getIn(m) as MapModel
      val model2 = editor2Path.getIn(m) as MapModel
      assertTrue(editorsTag.getIn(m).contains(model1))
      assertTrue(editorsTag.getIn(m).contains(model2))
      m
    }

    // first editor deleted
    model.transaction {
      editor1Path.putIn(it, AbsentModel())
    }
    model.transaction { m ->
      val model2 = editor2Path.getIn(m) as MapModel
      assertTrue(editorsTag.getIn(m).contains(model2))
      m
    }

    // second editor deleted
    model.transaction {
      Path("a").putIn(it, AbsentModel())
    }
    model.transaction { m ->
      assertTrue(editorsTag.getIn(m).count() == 0)
      m
    }

    assertEquals(5, counter.get())
  }
}