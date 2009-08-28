package com.intellij.openapi.vfs.impl.local;

public aspect VfsLocalImplUse {
  pointcut everyUseOfImpl() : call(* com.intellij.openapi.vfs.impl.local..*.*(..)) || call (com.intellij.openapi.vfs.impl.local..*.new(..));

  pointcut outsideImpl() : !within(com.intellij.openapi.vfs.impl.local..*);

  declare warning : everyUseOfImpl() && outsideImpl(): "Classes from openapi.vfs.impl.local should only be used in openapi.vfs.impl.local";
}