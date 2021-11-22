import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PatternIfCanBeSwitch {

  void simplePatternMatching(@NotNull Object o) {
   <warning descr="'if' statement can be replaced with 'switch' statement">if</warning> (o instanceof Integer i) {
      // do this
    } else if (o instanceof String s) {
      // do that
    }
  }

  void guardedPatternMatching(@NotNull Object o) {
   <warning descr="'if' statement can be replaced with 'switch' statement">if</warning> (o instanceof Integer i && i > 0) {
      // do this
    } else if (o instanceof String s) {
      // do that
    }
  }

  void patternMatchingWithNull(@Nullable Object o) {
   <warning descr="'if' statement can be replaced with 'switch' statement">if</warning> (o instanceof Integer i && i > 0) {
      // do this
    } else if (o instanceof String s) {
      // do that
    } else if (o == null) {
     //throw something
    }
  }

  void patternMatchingWithNullInSameCondition(@Nullable Object o) {
   <warning descr="'if' statement can be replaced with 'switch' statement">if</warning> (o instanceof Integer i && i > 0 && i < 10) {
      // do this
    } else if (o instanceof String s || o == null) {
      // do that
    }
  }

  void patternMatchingImplicitNull(@Nullable Object o) {
   <warning descr="'if' statement can be replaced with 'switch' statement">if</warning> (o instanceof Integer i && i > 0) {
      // do this
    } else if (o instanceof String s) {
      // do that
    } else {
      // implicit null check
    }
  }

  void guardedMatchingWithCustomOrder(@Nullable Object o, int x) {
   <warning descr="'if' statement can be replaced with 'switch' statement">if</warning> (x > 0 && o instanceof Integer i) {
      // do this
    } else if (o instanceof String s) {
      // do that
    } else {
      // implicit null check
    }
  }

  void testSideEffectInGuard(Object obj) {
    int x = 0;
    if (x++ < 10 && obj instanceof String) {
      System.out.println(((String) obj).trim());
    } else if (x++ < 10 && obj instanceof Integer) {
      System.out.println(((Integer) obj).byteValue());
    } else {
      System.out.println("None");
    }
  }

  void patternMatchingWithoutNullCheck(@Nullable Object o) {
   //should be ignored if 'only suggest null safe' option is enabled
   if (o instanceof Integer i && i > 0) {
      // do this
    } else if (o instanceof String s) {
      // do that
    }
  }

  void multiplePatternsInOneCondition(@NotNull Object o){
    if (o instanceof Integer i && i > 0) {
      // do this
    } else if (o instanceof String s || o instanceof Integer i) {
      // case String s, Integer i -> is prohibited because of 'java: illegal fall-through to a pattern'
    }
  }

  void constatsWithPatternMatching(@NotNull Object o){
    if (o instanceof Integer i) {
      // do this
    } else if ("match".equals(o)) {
      // illegal due to: 'java: constant label of type java.lang.String is not compatible with switch selector type java.lang.Object'
    }
  }

  void dominatedCondition(@NotNull Object o){
    //results with constant duplicates are ignored
    /* if (x == 2) {
    } else if (x == 2) {
    } */
    //this should be ignored for consistency
    if (o instanceof CharSequence cs) {
      //do this
    } else if (o instanceof String s) {
      //dominated
    }
  }

  void dominatedCondition2(@NotNull Object o){
    if (o instanceof String s) {
      //do this
    } else if (o instanceof String s && s.length() > 3) {
      //dominated
    }
  }

}