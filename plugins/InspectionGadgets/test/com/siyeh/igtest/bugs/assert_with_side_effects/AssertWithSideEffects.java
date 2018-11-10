package com.siyeh.igtest.bugs.assert_with_side_effects;
import java.sql.*;
import java.util.*;

public class AssertWithSideEffects {
    private int sideEffect = 0;
    private boolean noEffect = false;
    void foo(boolean b) {
        assert !b && hasNoSideEffects();
    }

    void bar(int i) {
        <warning descr="'assert' has side effects">assert</warning> i++ < 10;
    }

    void abc() {
        <warning descr="'assert' has side effects">assert</warning> isSideEffect();
    }

    boolean isSideEffect() {
        sideEffect = 1;
        return true;
    }

    boolean hasNoSideEffects() {
        assert !noEffect;
        return noEffect;
    }

    void jdbc(ResultSet rs) throws SQLException {
      <warning descr="'assert' has side effects">assert</warning> rs.last();
    }
}