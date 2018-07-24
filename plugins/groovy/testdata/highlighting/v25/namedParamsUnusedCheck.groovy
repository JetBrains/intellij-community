import groovy.transform.NamedParam
import groovy.transform.NamedVariant

@NamedVariant
static String bar(String s1, @NamedParam s2) {
  "$s1 $s2"
}

println bar("foo", s2: "bar")