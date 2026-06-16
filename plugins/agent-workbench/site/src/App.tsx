import Hero from './components/Hero'
import Features from './components/Features'
import Providers from './components/Providers'
import Footer from './components/Footer'

export default function App() {
  return (
    <>
      <Hero />
      <main>
        <Features />
        <Providers />
      </main>
      <Footer />
    </>
  )
}
