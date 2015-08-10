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

import com.jetbrains.reactivemodel.mapping.Ignore
import com.jetbrains.reactivemodel.mapping.Mapper
import com.jetbrains.reactivemodel.mapping.Mapping
import com.jetbrains.reactivemodel.mapping.Original
import com.jetbrains.reactivemodel.mapping.model.ModelBean

/**
 * @author serce
 * @since 09.08.15.
 */
class User(name: String, surname: String) {
    private val name: String;
    private val surname: String;

    init {
        this.name = name
        this.surname = surname
    }

    public fun getName(): String {
        return name;
    }

    public fun getSurname(): String {
        return surname;
    }
}

@Mapping(javaClass<User>())
data class MyUser(val name: String, val surname: String) : ModelBean


@Mapping(javaClass<Car>())
class MiniCar : ModelBean {
    private var name: String = "";
    private var year: Int = 0;

    public fun getYear(): Int {
        return year;
    }

    public fun setYear(year: Int) {
        this.year = year;
    }

    public fun getName(): String {
        return name;
    }

    public fun setName(name: String) {
        this.name = name;
    }
}

class Car() {
    public var name: String = "";
    public var year: Int = 0
}

data class Garage(val user: User, val name: String)

@Mapping(javaClass<Garage>())
data class Garage2(val user: MyUser, val name: String) : ModelBean

data class Garage3(val user: User, val name: String)

@Mapping(javaClass<Garage3>())
class Garage4() : ModelBean {
    public var user: MyUser? = null
    public var name: String = ""
}


data class Chel(val name: String, val init: Boolean)

@Mapping(javaClass<Chel>())
data class Chel2(@Original("name") val fio: String,
                 val init: Boolean) : ModelBean {

    @Ignore val adopt: Long = 0
}


data class MyColor(val value: Int)

@Mapping(javaClass<MyColor>(), javaClass<ColorMapper>())
data class StrColor(val str: String) : ModelBean

class ColorMapper : Mapper<MyColor, StrColor> {
    override fun map(obj: MyColor): StrColor = StrColor(obj.value.toString())
}

data class MyIntValue(val value: Int)

@BeanMapper(javaClass<MyIntValue>(), javaClass<String>())
class MyIntValueMapper() : Mapper<MyIntValue, String> {
    override fun map(obj: MyIntValue): String {
        return obj.value.toString()
    }
}

class Nillable(val name: String, val surn: String?)

class NillableBean(val name: String, val surn: String)