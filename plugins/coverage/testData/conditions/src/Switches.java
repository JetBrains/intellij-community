public class Switches {

  void singleBranchSwitch1(int x) {
    switch (x) {
      case 1: {
        System.out.println("Case 1");
        break;
      }
      case 2: {
        System.out.println("Case 2");
        break;
      }
    }
  }

  void singleBranchSwitch2(int x) {
    switch (x) {
      case 1: {
        System.out.println("Case 1");
        break;
      }
      case 2: {
        System.out.println("Case 2");
        break;
      }
    }
  }

  void defaultBranchSwitch(int x) {
    switch (x) {
      case 1: {
        System.out.println("Case 1");
        break;
      }
      case 2: {
        System.out.println("Case 2");
        break;
      }
      default: {
        System.out.println("Default");
        break;
      }
    }
  }

  void fullyCoveredSwitch(int x) {
    switch (x) {
      case 1: {
        System.out.println("Case 1");
        break;
      }
      case 2: {
        System.out.println("Case 2");
        break;
      }
    }
  }

  void fullyCoveredSwitchWithDefault(int x) {
    switch (x) {
      case 1: {
        System.out.println("Case 1");
        break;
      }
      case 2: {
        System.out.println("Case 2");
        break;
      }
      default: {
        System.out.println("Default");
        break;
      }
    }
  }

  void fullyCoveredSwitchWithoutDefault(int x) {
    switch (x) {
      case 1: {
        System.out.println("Case 1");
        break;
      }
      case 2: {
        System.out.println("Case 2");
        break;
      }
      default: {
        System.out.println("Default");
        break;
      }
    }
  }

  void switchWithFallThrough(int x) {
    switch (x) {
      case 1: {
        System.out.println("Case 1");
      }
      case 2: {
        System.out.println("Case 2");
        break;
      }
    }
  }

  void fullyCoveredSwitchWithImplicitDefault(int x) {
    switch (x) {
      case 1: {
        System.out.println("Case 1");
        break;
      }
      case 2: {
        System.out.println("Case 2");
        break;
      }
    }
  }

  void stringSwitch(String s) {
    switch (s) {
      case "C": {
        System.out.println("Case C");
        break;
      }
      case "B": {
        System.out.println("Case B");
        break;
      }
      case "A": {
        System.out.println("Case A");
        break;
      }
      case "D": {
        System.out.println("Case D");
        break;
      }
      case "E": {
        System.out.println("Case E");
        break;
      }
      case "F": {
        System.out.println("Case F");
        break;
      }
      case "G": {
        System.out.println("Case G");
        break;
      }
    }
  }

  void fullStringSwitch(String s) {
    switch (s) {
      case "A": {
        System.out.println("Case A");
        break;
      }
      case "B": {
        System.out.println("Case B");
        break;
      }
      default: {
        System.out.println("Default");
        break;
      }
    }
  }

  void stringSwitchSameHashCode(String s) {
    switch (s) {
      case "Aa": {
        System.out.println("Case A");
        break;
      }
      case "BB": {
        System.out.println("Case B");
        break;
      }
      default: {
        System.out.println("Default");
        break;
      }
    }
  }

  void tableSwitch(int x) {
    switch (x) {
      case 4: {
        System.out.println("Case 4");
        break;
      }
      case 3: {
        System.out.println("Case 3");
        break;
      }
      case 2: {
        System.out.println("Case 2");
        break;
      }
      case 1: {
        System.out.println("Case 1");
        break;
      }
    }
  }

}
