enum class SomeEnum {
    <caret>UNUSED;
    companion object{
        fun values(){

        }

        fun main(){
            values()
        }
    }
}
// IGNORE_K2