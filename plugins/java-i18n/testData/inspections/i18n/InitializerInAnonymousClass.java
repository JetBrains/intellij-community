@SuppressWarnings("FooBar")
class Test {
  public static void main(String[] args){
    ActionListener listener = new ActionListener(){
      {
        final String test = <warning descr="Hardcoded string literal: \"problem reported twice\"">"problem reported twice"</warning>;
      }
      public void actionPerformed(final ActionEvent e) {

      }
    };
  }
}
interface ActionListener {
  public void actionPerformed(ActionEvent e);
}
interface ActionEvent {}