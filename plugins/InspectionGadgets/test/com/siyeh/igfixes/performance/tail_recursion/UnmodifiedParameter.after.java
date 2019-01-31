import java.awt.*;

class UnmodifiedParameter {

  private static boolean hasAncestor(Container child, Container ancestor) {
      while (true) {
          if (child == null) {
              return false;
          }

          if (ancestor == null) {
              return true;
          }

          if (child.getParent() == ancestor) {
              return true;
          }

          child = child.getParent();
      }
  }
}