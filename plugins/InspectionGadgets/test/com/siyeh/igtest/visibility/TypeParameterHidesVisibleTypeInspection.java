package com.siyeh.igtest.visibility;
import java.util.*;

public abstract class TypeParameterHidesVisibleTypeInspection<List> {
   private Map map = new HashMap();

    public abstract List foo();

    public abstract <Set> Set bar();
    public abstract <TypeParameterHidesVisibleTypeInspection> TypeParameterHidesVisibleTypeInspection baz();
    public abstract <InputStream> InputStream baz3();
    public abstract <A> A baz2();
}
