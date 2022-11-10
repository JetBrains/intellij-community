import { makeMap, isBuiltInTag, cached, no } from 'shared/util'
import { ASTElement, CompilerOptions, ASTNode } from 'types/compiler'
let isStaticKey
let isPlatformReservedTag
const genStaticKeysCached = cached(genStaticKeys)
export function optimize(
root: ASTElement | null | undefined,
options: CompilerOptions
) {
if (!root) return
isStaticKey = genStaticKeysCached(options.staticKeys || '')
isPlatformReservedTag = options.isReservedTag || no
markStatic(root)
markStaticRoots(root, false)
}
function genStaticKeys(keys: string): Function {
return makeMap(
'type,tag,attrsList,attrsMap,plain,parent,children,attrs,start,end,rawAttrsMap' +
(keys ? ',' + keys : '')
)
}
function markStatic(node: ASTNode) {
node.static = isStatic(node)
if (node.type === 1) {
if (
!isPlatformReservedTag(node.tag) &&
node.tag !== 'slot' &&
node.attrsMap['inline-template'] == null
) {
return
}
for (let i = 0, l = node.children.length; i < l; i++) {
const child = node.children[i]
markStatic(child)
if (!child.static) {
node.static = false
}
}
if (node.ifConditions) {
for (let i = 1, l = node.ifConditions.length; i < l; i++) {
const block = node.ifConditions[i].block
markStatic(block)
if (!block.static) {
node.static = false
}
}
}
}
}
function markStaticRoots(node: ASTNode, isInFor: boolean) {
if (node.type === 1) {
if (node.static || node.once) {
node.staticInFor = isInFor
}
if (
node.static &&
node.children.length &&
!(node.children.length === 1 && node.children[0].type === 3)
) {
node.staticRoot = true
return
} else {
node.staticRoot = false
}
if (node.children) {
for (let i = 0, l = node.children.length; i < l; i++) {
markStaticRoots(node.children[i], isInFor || !!node.for)
}
}
if (node.ifConditions) {
for (let i = 1,  l = node.ifConditions.length; i < l; i++) {
markStaticRoots(node.ifConditions[i].block, isInFor)
}
}
}
}
function isStatic(node: ASTNode): boolean {
if (node.type === 2) {
return false
}
if (node.type === 3) {
return true
}
return !!(
node.pre ||
(!node.hasBindings && 
!node.if &&
!node.for && 
!isBuiltInTag(node.tag) && 
isPlatformReservedTag(node.tag) && 
!isDirectChildOfTemplateFor(node) &&
Object.keys(node).every(isStaticKey))
)
}
function isDirectChildOfTemplateFor(node: ASTElement): boolean {
while (node.parent) {
node = node.parent
if (node.tag !== 'template') {
return false
}
if (node.for) {
return true
}
}
return false
}
