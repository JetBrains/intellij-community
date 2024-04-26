package pkg;

public class TestSwitchSimpleReferencesJavac {

  private static void testString(String r2) {
    switch (r2) {
      case "first":
        System.out.println("1");
        break;
      case "second":
        System.out.println("2");
      case "third":
        System.out.println("3");
        break;
      case null, default:
        System.out.println("default null");
    }
  }

  private static void testString2(String r2) {
    switch (r2) {
      case "first":
        System.out.println("1");
        break;
      case "second":
        System.out.println("2");
        break;
      case String s:
        System.out.println(s);
    }
  }

  private static void testString3(String r2) {
    switch (r2) {
      case "first":
        System.out.println("1");
        break;
      case "second":
        System.out.println("2");
        break;
      case null:
        System.out.println("null");
        break;
      case String s:
        System.out.println(s);
    }
  }


  private static void testByte(Byte r2) {
    switch (r2) {
      case 1:
        break;
      case 2:
        System.out.println("2");
        break;
      case null:
      default:
        System.out.println("default, null");
    }
  }

  private static void testChar(Character r2) {
    switch (r2) {
      case '1':
      case '2':
        System.out.println("2");
        break;
      case null:
      default:
        System.out.println("default, null");
    }
  }


  private static void testInt(Integer r2) {
    switch (r2) {
      case 1:
        break;
      case 3:
        System.out.println("2");
        break;
      case null:
      default:
        System.out.println("default, null");
    }
  }

  private static void testInt2(Integer r2) {
    switch (r2) {
      case 1:
        break;
      case 3:
        System.out.println("2");
        break;
      default:
        System.out.println("default");
    }
  }

  enum Numbers {
    FIRST, SECOND
  }

  private static void testEnum(Numbers r2) {
    switch (r2) {
      case Numbers.FIRST:
        break;
      case SECOND:
        System.out.println("2");
        break;
      case null:
        System.out.println("null");
    }
  }

  private static void testEnum2(Numbers r2) {
    switch (r2) {
      case Numbers.FIRST:
        break;
      case SECOND:
        System.out.println("2");
        break;
      case null:
        System.out.println("null");
    }
  }

  private static void testEnum3(Numbers r2) {
    switch (r2) {
      case Numbers.FIRST:
        break;
      case SECOND:
        System.out.println("2");
        break;
      case null:
        System.out.println("null");
        break;
      case Numbers n:
        System.out.println(n);
    }
  }

  private static void testEnum4(Numbers r2) {
    switch (r2) {
      case Numbers.FIRST:
        break;
      case SECOND:
        System.out.println("2");
        break;
      default:
        System.out.println("default");
    }
  }

  private static void testEnum5(Numbers r2) {
    switch (r2) {
      case SECOND:
        System.out.println("2");
        break;
      case null, default:
        System.out.println("default");
    }
  }
}