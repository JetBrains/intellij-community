import java.util.*;
import java.io.*;
<warning descr="Commented out code (1 line)">//</warning> import java.awt.List;

class CommentedOutCode /* extends Object */ {
  // https://gcc.gnu.org/onlinedocs/cpp/Standard-Predefined-Macros.html
  // https://youtrack.jetbrains.com/issue/IDEA-71996

  //// VARIABLE STATE \\\\
  private String s;

  <warning descr="Commented out code (1 line)">//</warning> void x() {}
  private String field;


  private static int danglingElse(int i) {
    if (i == 3) {
      System.out.println(i);
    }
    <warning descr="Commented out code (3 lines)">//</warning>else if (i == 4) {
    //  System.exit(-1);
    //}
    return i;
    //else if (i == 4) {
    //  System.exit(-1);
    //}
  }

  private void danglingElse2(int i) {
    if (i == 3) {
      System.out.println(i);
    } else {

    }
    //else if (i == 4) {
    //  System.exit(-1);
    //}

    if (i == 3) {
      System.out.println(i);
    } else if (i == 0) {

    }
    <warning descr="Commented out code (3 lines)">//</warning>else if (i == 4) {
    //  System.exit(-1);
    //}
  }

  int x(int i) {
    new Object() {

      <warning descr="Commented out code (3 lines)">/*</warning>public String toString() {
        return "";
        }*/
    };
    <warning descr="Commented out code (1 line)"><caret>//</warning> System.out.println(i);
    return i + 1 /*+ 2*/;
    // https://youtrack.jetbrains.com/issue/CPP-3936 Move members dialog choses arbitrary file by name, if there are several in project
    // https://youtrack.jetbrains.com/issue/CPP-3935 Move members dialog doesn't recognize case insensitive file names
    // https://youtrack.jetbrains.com/issue/CPP-3937 Move members dialog doesn't recognize existing non source files
  }

  //TODO highlight parameters in macro substitution (in macro definition)
  <warning descr="Commented out code (4 lines)">//</warning>@Override
  //public void visitMacroParameter(OCMacroParameterImpl parameter) {
  //  highlight(parameter, OCHighlightingKeys.MACRO_PARAMETER);
  //}
  //

  /*
    List<String> tmp = map.get(s.length());
    if(tmp == null) {
        tmp = new ArrayList<>();
        map.put(s.length(), tmp);
    }
    tmp.add(s);
   */
  void x(String s, String... ss) {}

  void x() {
    // file://C:/Windows/System32/Config
    <warning descr="Commented out code (1 line)">//</warning> System.out.println();
  }

  void k() {
    //noinspection unchecked
    l(new ArrayList());
    // TODO:
  }
  void l(List<String> l) {
    //noinspection one,two
    System.out.println(); // Smiles("[C+]");
    // "DROP VIEW $viewName$";
  }

  // TODO: change to (uri -> url)
  // uri -> path
  public String fromUri(String uri) {
    try {
      new FileInputStream(uri);
    }
    catch (FileNotFoundException e) {
      //ignore;
    }

    // was: true
    return null;
    // test
    //
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Parser
  //
  //////////////////////////////////////////////////////////////////////////////////////////
  public boolean value(final String file) {
    // Blocked by current false-positives
    // https://youtrack.jetbrains.com/issue/CPP-11252
    <warning descr="Commented out code (1 line)">//</warning>return OCAnnotator.isAnnotated(myProject, file);
    return false;
  }

  // fixme we've got a race here. RailsFacet is not yet updated configs, bug tree already updated RUBY-22574
  <warning descr="Commented out code (41 lines)">/*</warning>
  public void testRenameInHomeFolder_HomeRoot() throws IncorrectOperationException {
    final JTree tree = initTree();

    PlatformTestUtil.assertTreeEqual(tree,
                                 "-Project\n" +
                                 " -Module\n" +
                                 "  -Config\n" +
                                 "   -User Folder: environment\n" +
                                 "    File: file.rb\n" +
                                 "   File: some.yml\n" +
                                 "  -Lib: Lib\n" +
                                 "   -User Folder: folder\n" +
                                 "    -User Folder: subfolder\n" +
                                 "     File: file.rb\n" +
                                 "    File: readme.txt\n" +
                                 "   File: some.yml\n" +
                                 "  -RSpec\n" +
                                 "   -User Folder: folder\n" +
                                 "    File: some_spec.rb\n" +
                                 "   File: readme.txt\n");

    renameFileOrFolderAndWait("", "other_rails");

    PlatformTestUtil.assertTreeEqual(tree,
                                 "-Project\n" +
                                 " -Module\n" +
                                 "  -Config\n" +
                                 "   -User Folder: environment\n" +
                                 "    File: file.rb\n" +
                                 "   File: some.yml\n" +
                                 "  -Lib: Lib\n" +
                                 "   -User Folder: folder\n" +
                                 "    -User Folder: subfolder\n" +
                                 "     File: file.rb\n" +
                                 "    File: readme.txt\n" +
                                 "   File: some.yml\n" +
                                 "  -RSpec\n" +
                                 "   -User Folder: folder\n" +
                                 "    File: some_spec.rb\n" +
                                 "   File: readme.txt\n");
  }
  */

}
enum E {
  A {
    <warning descr="Commented out code (3 lines)">/*</warning>
    public String toString() {
    return "";
    }
     */
  }
}