package com.siyeh.igtest.controlflow.duplicate_boolean_branch;

public class DuplicateBooleanBranch {

  boolean x(boolean b, boolean c){
    return b && b && c;
  }
}