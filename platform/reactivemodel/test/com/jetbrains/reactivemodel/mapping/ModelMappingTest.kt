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
package com.jetbrains.reactivemodel.mapping

import com.jetbrains.reactivemodel.mapping.model.ModelMapper
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class ModelMappingTest {
  @Test
  public fun primitiveMap() {
    assertEquals(ModelMapper.map("Hello"), PrimitiveModel("Hello"))
    assertEquals(ModelMapper.map(1), PrimitiveModel(1))
  }

  @Test
  public fun constructorMap() {
    val user = User("Anton", "Petrov")
    val myUser = user.toModel()

    myUser as MapModel
    assertEquals(PrimitiveModel(user.getName()), myUser["name"])
    assertEquals(PrimitiveModel(user.getSurname()), myUser["surname"])
  }

  @Test
  public fun getSetTest() {
    val car = Car()
    car.name = "Chevro"
    car.year = 1992
    val mini = car.toModel() as MapModel
    assertEquals(PrimitiveModel(car.name), mini["name"])
    assertEquals(car.year, value(mini["year"]))
  }

  @Test
  public fun recursiveTest() {
    val gar = Garage(User("Nikolay", "Ivanov"), "my garage")
    val gar2 = gar.toModel() as MapModel

    val user = gar2["user"] as MapModel

    assertEquals(gar.user.getName(), value(user["name"]))
    assertEquals(gar.user.getSurname(), value(user["surname"]))

    assertEquals(gar.name, value(gar2["name"]))
  }

  @Test
  public fun mapperTest() {
    val color = MyColor(20)
    val col2 = color.toModel() as MapModel
    assertTrue(col2.size() == 1)
    assertEquals(PrimitiveModel(color.value.toString()), col2["str"])
  }

  private fun value(model: Any?) = (model as PrimitiveModel<*>).value
}
