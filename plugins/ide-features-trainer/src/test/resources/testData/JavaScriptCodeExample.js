globalVar = 12342
globalString = "123123" + "eee" + '11'

function globalFunction(arg1, arg2) {
    console.log(arg1 + arg2 - arg1 * arg1)
}

class JavaScriptCodeExample {
    static field = 322
    abc = 3

    constructor(abc) {
        this.abc = abc;
    }

    main(args) {
        let anotherClass = new AnotherClass()
        var abc = 3
        const bcd = 5 * abc + JavaScriptCodeExample.field
        this.func(abc)
        if(abc < bcd && globalFunction(0, abc)) {
            console.log(abc * bcd - JavaScriptCodeExample.field)
        }
        let exampleClass = new JavaScriptCodeExample(322)
        exampleClass.main(null)
    }

    func(arg) {
        return arg
    }

}

class AnotherClass {
    field = 322

    main(args) {
        this.funct(this.funct(this.field * this.field))
        console.log(this.field * this.field)
    }

    funct(arg) {
        return arg && this.field == 0
    }

    cyclesFunction(ere) {
        let i = 10
        for(let j = 0; j < i; j++) {
            var res = j - j + j
            console.log(res - j)
        }
        i += 10
        while(i > 0) {
            console.log(i * i)
            i--
        }
        if(true) { i++ }
        return i
    }

    stringFunction(sss) {
        let str = '2' + "123" + "rrr"
        str += '11111' + '323' + 2
        return str.toString() + "1231" + str + '11'
    }

    selfReturningFunction(arg) {
        return this
    }

    functionForReplaceCompletion() {
        let instance = new AnotherClass()
        let ss = instance.selfReturningFunction(322).selfReturningFunction(instance.cyclesFunction(0))
            .selfReturningFunction(1).field
    }
}
