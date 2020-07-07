import java.util.*;

class CommentedOutCode /* extends Object */ {
  // https://gcc.gnu.org/onlinedocs/cpp/Standard-Predefined-Macros.html
  // https://youtrack.jetbrains.com/issue/IDEA-71996

  //// VARIABLE STATE \\\\
  private String s;

  int x(int i) {
      return i + 1 /*+ 2*/;
    // https://youtrack.jetbrains.com/issue/CPP-3936 Move members dialog choses arbitrary file by name, if there are several in project
    // https://youtrack.jetbrains.com/issue/CPP-3935 Move members dialog doesn't recognize case insensitive file names
    // https://youtrack.jetbrains.com/issue/CPP-3937 Move members dialog doesn't recognize existing non source files
  }

  //TODO highlight parameters in macro substitution (in macro definition)

    void k() {
    //noinspection unchecked
    l(new ArrayList());
    // TODO:
  }
  void l(List<String> l) {
    //noinspection one,two
    System.out.println();
    // "DROP VIEW $viewName$";
  }

  // TODO: change to (uri -> url)
  // uri -> path
  public String fromUri(String uri) {
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
      return false;
  }

  // fixme we've got a race here. RailsFacet is not yet updated configs, bug tree already updated RUBY-22574

}