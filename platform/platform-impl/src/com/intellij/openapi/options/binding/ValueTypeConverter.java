package com.intellij.openapi.options.binding;

/**
 * @author Dmitry Avdeev
 */
public abstract class ValueTypeConverter<A, B> {

  public abstract A to(B b);
  public abstract B from(A a);
  public abstract Class<A> getSourceType();
  public abstract Class<B> getTargetType();

  public static final ValueTypeConverter<String, Integer>STRING_2_INTEGER = new ValueTypeConverter<String, Integer>() {
    @Override
    public String to(Integer integer) {
      return integer.toString();
    }

    @Override
    public Integer from(String s) {
      return Integer.decode(s);
    }

    @Override
    public Class<String> getSourceType() {
      return String.class;
    }

    @Override
    public Class<Integer> getTargetType() {
      return Integer.class;
    }
  };

  public static final ValueTypeConverter[] STANDARD = new ValueTypeConverter[] {
    STRING_2_INTEGER
  };

}
