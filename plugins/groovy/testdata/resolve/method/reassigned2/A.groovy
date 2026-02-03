def r(String s) {}
def r(int i) {}

def ttt(){
  def t

  t = ""
  r(t)
  t = 0
  <caret>r(t)
}