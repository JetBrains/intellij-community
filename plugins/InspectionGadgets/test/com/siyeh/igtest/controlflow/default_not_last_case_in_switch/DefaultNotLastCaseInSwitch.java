class MyTest {
    void main(int n) {
        int i = switch (n) {
            <warning descr="'default' branch not last case in switch expression">default</warning> -> {
                break 7;
            }
            case 0 -> 8;
        };

        int j = switch (n) {
          <warning descr="'default' branch not last case in switch expression">default</warning>:
                break 7;
          case 0: break 8;
        };

        switch (n) {
          <warning descr="'default' branch not last case in switch statement">default</warning>:
            System.out.println();
          case 1:
            break;
        }
        switch (n) {
          default:
            System.out.println();
        }
    }
}