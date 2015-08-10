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

import com.jetbrains.reactivemodel.mapping.KDM
import org.junit.Test
import kotlin.test.assertEquals

public class SimpleMapTest {

    @Test
    public fun primitiveMap() {
        assertEquals(KDM.map("Hello"), "Hello")
        assertEquals(KDM.map(1), 1)
    }

    @Test
    public fun constructorMap() {
        val user = User("Anton", "Petrov")
        val myUser = KDM.map<MyUser>(user)

        assertEquals(user.getName(), myUser.name)
        assertEquals(user.getSurname(), myUser.surname)
    }

    @Test
    public fun getSetTest() {
        val car = Car()
        car.name = "Chevro"
        car.year = 1992
        val mini = KDM.map<MiniCar>(car)
        assertEquals(car.name, mini.getName())
        assertEquals(car.year, mini.getYear())
    }

    @Test
    public fun recursiveTest() {
        val gar = Garage(User("Nikolay", "Ivanov"), "my garage")
        val gar2 = KDM.map<Garage2>(gar)

        assertEquals(gar.user.getName(), gar2.user.name)
        assertEquals(gar.user.getSurname(), gar2.user.surname)

        assertEquals(gar.name, gar2.name)
    }

    @Test
    public fun recursiveTest2() {
        val gar = Garage3(User("Nikolay", "Ivanov"), "my garage")
        val gar2 = KDM.map<Garage4>(gar)

        assertEquals(gar.user.getName(), gar2.user!!.name)
        assertEquals(gar.user.getSurname(), gar2.user!!.surname)

        assertEquals(gar.name, gar2.name)
    }

    @Test
    public fun originalNameTest() {
        val chel = Chel("Alex", true)
        val chel2 = KDM.map<Chel2>(chel)

        assertEquals(chel.name, chel2.fio)
        assertEquals(chel.init, chel2.init)
        assertEquals(0L, chel2.adopt)
    }

    @Test
    public fun mapperTest() {
        val color = MyColor(20)
        val col2 = KDM.map<StrColor>(color)
        assertEquals(color.value.toString(), col2.str)
    }

    @Test
    public fun annoMapperTest() {
        val myInt = MyIntValue(42)
        assertEquals("42", KDM.map(myInt))
    }

}