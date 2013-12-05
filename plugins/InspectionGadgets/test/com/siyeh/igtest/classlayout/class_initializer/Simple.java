package com.siyeh.igtest.classlayout.class_initializer;

public class Simple {

  <warning descr="Non-'static' initializer">{</warning>}

  Simple() {}
}