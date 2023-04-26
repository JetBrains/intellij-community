package com.example.demo

import com.intellij.openapi.components.PersistentStateComponent

abstract class MyServiceBase : PersistentStateComponent<Int>

class MyService : MyServiceBase()