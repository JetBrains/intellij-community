// "Replace inheritance with qualified references in this class" "true"
public class ConstImpl implements <caret>Const{
    {
        int i = DDDD;
    }
}
interface Const {
  int DDDD = 0;
}
