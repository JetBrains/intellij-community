import com.intellij.openapi.extensions.ExtensionPointName

 class MyStringEPViaConstant {

   companion object {
     val EP_ID = "com.intellij.myStringEP"

     val EP_<caret>NAME = ExtensionPointName.create<String>(EP_ID)
   }
 }
