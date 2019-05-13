package com.siyeh.igtest.inheritance.abstract_method_with_missing_implementations;

class Temp35 {
  public Workable quintoSmart() { return new Workable() {
    @Override public void work() { }
  }; }

  public interface Workable { void work(); }
  <error descr="Duplicate class: 'Workable'">public interface Workable</error> { void work(); }

  public static void main(String[] args) {
    abstract class Local { abstract void work(); }
    <error descr="Duplicate class: 'Local'">abstract class Local</error> { abstract void work(); }
    new Local() {
      @Override public void work() { }
    };
  }

}