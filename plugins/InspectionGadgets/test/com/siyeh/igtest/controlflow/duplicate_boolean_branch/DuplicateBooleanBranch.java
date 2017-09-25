package com.siyeh.igtest.controlflow.duplicate_boolean_branch;

public class DuplicateBooleanBranch {

  boolean x(boolean b, boolean c){
    return <warning descr="Duplicate branch 'b'">b</warning> && <warning descr="Duplicate branch 'b'">b</warning> && c;
  }
}