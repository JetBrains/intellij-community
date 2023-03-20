package git4idea.attributes;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GitCheckAttrParserTest {

  @Test
  public void test() {
    String output = """

      org/example/MyClass.java: crlf: unset
      org/example/MyClass.java: diff: java
      org/example/MyClass.java: myAttr: set
      """;
    GitCheckAttrParser parse = GitCheckAttrParser.parse(Arrays.asList(output.split("\n")));
    String path = "org/example/MyClass.java";
    assertTrue("Missing path", parse.getAttributes().containsKey(path));
    Collection<GitAttribute> attrs = parse.getAttributes().get(path);
    assertTrue("CRLF attribute not found", attrs.contains(GitAttribute.CRLF));
    assertEquals("Too many attributes", 1, attrs.size());
  }
}
