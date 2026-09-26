package com.example.demo;

import com.intellij.openapi.components.PersistentStateComponent;

abstract class MyServiceBase implements PersistentStateComponent<Integer> { }

final class MyService extends MyServiceBase { }