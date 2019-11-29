package com.siyeh.igtest.bugs;

public class ReturnNullFromNotNullAnnotatedMethods {

  @NotNull
  private Object nullInsteadOfObject() {
    return <warning descr="Return of 'null'">null</warning>;
  }

  private Object nullInsteadOfObjectWithoutAnnotation() {
    return null;
  }

  @NotNull
  private int[] nullInsteadOfArray() {
    return <warning descr="Return of 'null'">null</warning>;
  }

  private int[] nullInsteadOfArrayWithoutAnnotation() {
    return null;
  }

  enum MyEnum{ A, B, C }

  @NotNull
  private String nullFromSwitchDefaultBranch(final MyEnum myEnum) {
    switch(myEnum){
      case A:
      case B:
        return "YES";
      default: return <warning descr="Return of 'null'">null</warning>;
    }
  }

  private String nullFromSwitchDefaultBranchWithoutAnnotation(final MyEnum myEnum) {
    switch(myEnum){
      case A:
      case B:
        return "YES";
      default: return null;
    }
  }

  @NotNull
  private String nullFromUnreachableSwitchDefaultBranch(final MyEnum myEnum) {
    switch(myEnum){
      case A:
      case B:
      case C:
        return "YES";
      default: return <warning descr="Return of 'null'">null</warning>;
    }
  }

  private String nullFromUnreachableSwitchDefaultBranchWithoutAnnotation(final MyEnum myEnum) {
    switch(myEnum){
      case A:
      case B:
      case C:
        return "YES";
      default: return null;
    }
  }

  @NotNull
  private String nullFromUnreachableReturnAfterSwitch(final MyEnum myEnum) {
    switch(myEnum){
      case A:
      case B:
      case C:
        return "YES";
    }
    return <warning descr="Return of 'null'">null</warning>;
  }

  private String nullFromUnreachableReturnAfterSwitchWithoutAnnotation(final MyEnum myEnum) {
    switch(myEnum){
      case A:
      case B:
      case C:
        return "YES";
    }
    return null;
  }
}