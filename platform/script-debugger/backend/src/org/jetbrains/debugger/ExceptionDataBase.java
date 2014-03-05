package org.jetbrains.debugger;

public abstract class ExceptionDataBase implements ExceptionData {
  private final Value exceptionValue;

  protected ExceptionDataBase(Value exceptionValue) {
    this.exceptionValue = exceptionValue;
  }

  @Override
  public final Value getExceptionValue() {
    return exceptionValue;
  }
}