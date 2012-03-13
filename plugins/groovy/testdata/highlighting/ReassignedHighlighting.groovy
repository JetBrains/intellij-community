def <info textAttributesKey="Groovy method declaration">method</info>(int <info textAttributesKey="Groovy parameter">param1</info>, int <info textAttributesKey="Groovy reassigned parameter">param2</info>) {
  int <info textAttributesKey="Groovy var">var1</info> = 0
  int <info textAttributesKey="Groovy reassigned var">var2</info> = 1
  if (<info textAttributesKey="Groovy parameter">param1</info> == 1) {
    <info textAttributesKey="Groovy reassigned parameter">param2</info> = <info textAttributesKey="Groovy reassigned var">var2</info> = 2
  }
  <info textAttributesKey="Method call">println</info> <info textAttributesKey="Groovy var">var1</info> +
     <info textAttributesKey="Groovy reassigned var">var2</info> +
     <info textAttributesKey="Groovy parameter">param1</info> +
     <info textAttributesKey="Groovy reassigned parameter">param2</info>
}

<info textAttributesKey="Method call">method</info>(239, 42)