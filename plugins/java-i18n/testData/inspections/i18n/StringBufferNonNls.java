import java.util.Collection;

class SBTest {
    void foo(@org.jetbrains.annotations.NonNls Collection coll) {
        coll.add("aaa");
    }

    void foo(@org.jetbrains.annotations.NonNls StringBuffer buffer) {
        buffer.append("aaa");
        buffer.append("aaa").append("bbb").append("do not i18n this too");
    }
}
