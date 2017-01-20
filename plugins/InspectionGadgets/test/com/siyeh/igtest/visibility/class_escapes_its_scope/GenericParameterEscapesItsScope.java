/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import java.util.*;
public class GenericParameterEscapesItsScope {
  public List<A> as;
  public List<<warning descr="Class 'B' is exposed outside its defined scope">B</warning>> bs;

  public List<<warning descr="Class 'B' is exposed outside its defined scope">B</warning>> getBs() { return bs; }
  public void setBs(List<<warning descr="Class 'B' is exposed outside its defined scope">B</warning>> bs) { this.bs = bs; }

  public List<A> getAs() { return as; }
  public void setAs(List<A> as) { this.as = as; }

  public class Inner extends B implements Getter<B>, Setter<B> {
    public <warning descr="Class 'B' is exposed outside its defined scope">B</warning> b;
    @Override
    public <warning descr="Class 'B' is exposed outside its defined scope">B</warning> get() {
      return b;
    }
    @Override
    public void set(<warning descr="Class 'B' is exposed outside its defined scope">B</warning> b) {
      this.b = b;
    }
  }
  public Data<<warning descr="Class 'B' is exposed outside its defined scope">B</warning>> foo() {
    class Local extends B implements Data<B> {
      public B b;
      @Override
      public B get() {
        return b;
      }
      @Override
      public void set(B b) {
        this.b = b;
      }
    }
    return new Local();
  }

  public static class A {}
  static class B extends A {}

  public interface Getter<T> { T get(); }
  interface Setter<T> { void set(T t); }
  public interface Data<T> extends Getter<T>, Setter<T> { }
}